# Medical Project Backend — Architecture Deep Dive for US Healthcare Compliance

---

## Layer 1: Project Overview

This project is a **backend for a US medical practice management system (HIPAA / ONC compliant)**. It provides RESTful APIs for the frontend, managing patients, appointments, prescriptions, billing, instant messaging, and other core healthcare workflows.

Three compliance goals run through the entire architecture:

| Goal | Standard | Implementation |
|------|---------|---------------|
| **Data Encryption** | HIPAA Security Rule §164.312 | AES-256-GCM automatic encryption/decryption of sensitive fields |
| **Audit Trail** | HIPAA §164.312(b) | Async AOP business-level audit logging |
| **Access Control** | HIPAA §164.312(a)(1) | Okta OAuth2 + RBAC + method-level authorization |
| **Interoperability** | 21st Century Cures Act / ONC | HAPI FHIR R4 + USCDI extensions |

---

## Layer 2: Technology Stack

| Technology | Version | Purpose |
|------------|---------|---------|
| **Java** | 17 (LTS) | Runtime platform |
| **Spring Boot** | 3.4.1 | Framework skeleton |
| **Spring Security + Okta OAuth2** | 6.x | Authentication — Okta issues tokens, backend validates via JWKS |
| **Spring Data JPA + QueryDSL** | 6.x / 5.x | ORM + type-safe dynamic queries |
| **HAPI FHIR** | 7.4 | FHIR R4 native types (Bundle/Patient/Encounter/MedicationRequest) |
| **MySQL** | 8.0+ | Primary database |
| **Redis + Redisson** | 7.x / 3.40 | Cache + rate limiting |
| **Springdoc OpenAPI** | 2.7.0 | API documentation (Swagger UI at /doc.html) |
| **Lombok / Hutool** | latest | Boilerplate reduction + utilities |

**Explicitly excluded technologies:** MyBatis/MyBatis-Plus, Spring Cloud microservices, gRPC/GraphQL, MongoDB/Elasticsearch, message queues, Shiro, self-issued JWT.

---

## Layer 3: Project Directory Structure

```
medical-server/src/main/java/com/example/medical/
│
├── MedicalApplication.java              ← Spring Boot entry point
│
├── common/                              ← Shared infrastructure
│   ├── base/         BaseEntity (id/createTime/updateTime/isDeleted/version)
│   ├── config/       SecurityConfig, SecurityConfigDev, FhirConfig,
│   │                 AesCryptoUtil, AesAttributeConverter, AsyncConfig,
│   │                 CacheConfig, RateLimiterConfig, DataInitializer
│   ├── audit/        @Auditable, AuditLogAspect, AuditLogWriter, AuditLog
│   ├── enums/        ResultCode
│   ├── exception/    BusinessException, GlobalExceptionHandler
│   └── result/       Result<T>, PageResult<T>
│
├── security/                             ← Auth adapters
│   ├── JwtUtils        (Okta token parsing)
│   ├── JwtClaimMapper  (Okta claims → Spring Security)
│   └── LoginUser       (Current user principal)
│
├── util/                                 ← Utilities
│   └── CsvUtil
│
└── module/                               ← Business modules
    ├── system/       Users/Roles/Menus/Login (Okta OAuth2)
    ├── patient/      Patient management + PatientAuth separation + FHIR clinical summary
    ├── appointment/  Appointment scheduling (US visit type + CPT codes)
    ├── prescription/ Prescription management (NDC/RxNorm/DEA/controlled substance)
    ├── billing/      Billing management (insurance claim lifecycle + CPT/ICD-10)
    ├── chat/         Instant messaging (AES-encrypted content, SSE real-time push)
    ├── dashboard/    Dashboard statistics
    └── export/       Data export (CSV + audit + rate limit)
```

Each module internally follows a uniform five-layer structure: `controller → service → repository → entity → dto`.

---

## Layer 4: Layered Architecture — How Data Flows

Using "query patient list" as an example:

```
HTTP Request → Controller → Service → Repository → Database
                          ↓
                      Entity/DTO conversion (AesAttributeConverter auto-decrypts)
                          ↓
HTTP Response ← Controller ← Service ← Repository ← Database
```

### 4.1 Controller Layer — Single Responsibility: Route, Call Service, Wrap Result

```java
@RestController
@RequestMapping("/api/v1/patients")
@RequiredArgsConstructor
public class PatientController {

    private final PatientService patientService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")
    public Result<PageResult<PatientVO>> page(
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) String keyword) {
        return Result.ok(patientService.page(page, size, keyword));
    }
}
```

Controller's only job: accept parameters → call Service → wrap Result return. No business logic.

### 4.2 Service Layer — Business Logic, Two Annotations + One Deliberate Omission

```java
@Service
@RequiredArgsConstructor
public class PatientService {

    @Transactional
    @Auditable(module = "patient", action = "CREATE", phiAccess = true)
    public void create(PatientFormDTO dto) {
        patientRepository.save(dto.toEntity());
    }
}
```

| Annotation | Purpose |
|------------|---------|
| `@Transactional` | Transaction protection — auto-rollback on failure |
| `@Auditable(phiAccess = true)` | Async audit log — parameter values auto-masked as `[PHI]` |
| *(no `@Cacheable`)* | Patient ePHI is **deliberately NOT cached** in Redis — prevents decrypted data leakage |

### 4.3 Repository Layer — Spring Data JPA

```java
public interface PatientRepository
        extends JpaRepository<Patient, Long>,
                JpaSpecificationExecutor<Patient> {
}
```

Extends `JpaSpecificationExecutor` for dynamic conditional query composition (e.g., search by MRN or email).

### 4.4 Entity Layer — Data Model

```java
@Entity
@Table(name = "patient")
@SQLDelete(sql = "UPDATE patient SET is_deleted = 1 WHERE id = ? AND version = ?")   // Soft delete
@SQLRestriction("is_deleted = 0")                                                     // Auto-filter deleted
public class Patient extends BaseEntity {

    private String mrn;           // Medical Record Number (internal identifier)

    @Convert(converter = AesAttributeConverter.class)
    private String ssn;           // SSN — AES-256-GCM encrypted storage

    @Convert(converter = AesAttributeConverter.class)
    private String name;          // Patient name — encrypted

    private LocalDate dateOfBirth;
    private String sexAtBirth;    // M / F / U
    private String genderIdentity;
    private String race;          // OMB 5 categories (ONC USCDI requirement)
    private String ethnicity;     // Hispanic/Latino or Not

    @Convert(converter = AesAttributeConverter.class)
    private String phoneMobile;   // Mobile phone — encrypted

    private String email;
    // Structured US address: line1/line2/city/state/zip
    // Emergency contact, insurance payer/memberId/groupNumber
}
```

**Three core design decisions:**

1. **Soft Delete** — `@SQLDelete` + `@SQLRestriction`: `deleteById()` only sets `is_deleted=1`, never physically deletes. All queries auto-filter deleted records.

2. **Automatic Encryption/Decryption** — `@Convert(converter = AesAttributeConverter.class)`: encrypted fields are auto-encrypted on write and auto-decrypted on read via AES-256-GCM. Developers use them as plain strings in business code.

3. **Optimistic Locking** — `@Version private Integer version` in `BaseEntity`: prevents data overwrites when multiple users concurrently modify the same record.

### 4.5 DTO Layer

```
PatientFormDTO  — Frontend→Backend (form input)
PatientVO       — Backend→Frontend (SSN shows only last-4)
```

Entity/DTO conversion logic is encapsulated in DTOs' internal `fromEntity()` / `toEntity()` static factory methods. Controllers and Services never scatter conversion code.

---

## Layer 5: Security Authentication — Okta OAuth2 Resource Server

### 5.1 Authentication Architecture

```
Production:                          Development (h2/dev profile):
┌─────────┐                          ┌──────────────────┐
│  Okta   │ ← JWKS endpoint          │ SecurityConfigDev │
│  Issues  │    (RS256 public key)    │ Local JwtDecoder  │
│  JWT     │                          │ + JwtEncoder      │
│  MFA     │                          │ HMAC-SHA256       │
└────┬────┘                          └──────────────────┘
     │
     ▼
┌─────────────┐
│ JwtDecoder  │ ← Spring Boot auto-configuration
│ Validates    │   issuer-uri → JWKS endpoint
│ sig + expiry │
└──────┬──────┘
       ▼
┌──────────────┐
│JwtClaimMapper│ ← Okta claims → Spring Security
│ groups→ROLE_* │   groups → ROLE_ADMIN/DOCTOR/PATIENT
│ scp→SCOPE_*  │   uid → userId
└──────────────┘
```

### 5.2 Login Flow

**Staff (Admin/Doctor):**

```
1. POST /api/v1/auth/login { username, password }
2. AuthService local BCrypt verifies user
3. Calls Okta /v1/token (password grant, scope: openid profile email groups)
4. Okta returns access_token + refresh_token
5. Frontend stores token, subsequent requests: Authorization: Bearer <access_token>
6. Spring Security validates token signature via Okta JWKS public key
7. JwtClaimMapper extracts groups claim → ROLE_ADMIN / ROLE_DOCTOR
```

**Patient:**

```
1. POST /api/v1/patient/login { username, password }
2. Lookup PatientAuth → account disabled/lockout check (5 failures→15min lockout)
3. Local BCrypt password verification (no external IdP — patients are self-managed accounts)
4. DevJwtEncoder issues HS256 JWT with uid, roles=[PATIENT], scp=[patient/Patient.read, ...]
5. JwtClaimMapper extracts roles claim → ROLE_PATIENT
```

Patient auth is **always local**, regardless of environment. Staff auth continues to use the external IdP
(Okta / Auth0 / Cognito) for production, with the same `DevJwtEncoder` fallback in dev mode.

Patient tokens have a longer default expiry (24 hours, configurable via `app.security.patient-token-expiry-seconds`)
since there is no separate refresh token. When the token expires, the patient simply logs in again.

**Development:** When `app.security.dev-mode=true` is explicitly set, `SecurityConfigDev` issues a locally-signed HMAC-SHA256 JWT with `roles` + `uid` claims — scoped to dev and h2 profiles only. Patient auth uses local JWT in **all** environments, not just dev.

### 5.3 Token Refresh (Staff Only)

```
POST /api/v1/auth/refresh { refreshToken }
  → Okta /v1/token (grant_type=refresh_token)
  → Old refresh_token auto-invalidated on Okta side
  → Returns new access_token + refresh_token
  → Dev environment doesn't support refresh (re-login instead)
```

Patient tokens are long-lived (default 24h) with no refresh mechanism. Expired tokens require re-login.
This is appropriate for browser-SPA use where token revocation isn't needed.

### 5.4 RBAC Access Control

```java
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")   // Doctor or Admin
@PreAuthorize("hasRole('ADMIN')")               // Admin only
@PreAuthorize("hasRole('PATIENT')")             // Patient portal
```

RBAC model: User → Role → Menu/Permission → API access control. Five tables: `sys_user`, `sys_role`, `sys_menu` + join tables `sys_user_role`, `sys_role_menu`.

### 5.5 SecurityConfig

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
.headers(headers -> headers
    .httpStrictTransportSecurity(hsts -> hsts.includeSubDomains(true).maxAgeInSeconds(31536000))
    .contentTypeOptions(cfg -> {})
    .frameOptions(frame -> frame.deny())
    .xssProtection(xss -> {})
    .cacheControl(cache -> {}))
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh",
                     "/api/v1/patient/login", "/api/v1/patient/refresh",
                     "/api/v1/fhir/metadata").permitAll()
    .requestMatchers("/doc.html", "/webjars/**", "/v3/api-docs/**").permitAll()
    .anyRequest().authenticated())
.oauth2ResourceServer(oauth2 -> oauth2
    .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtClaimMapper)));
```

**Security Response Headers:** HSTS (1yr+subDomains), X-Content-Type-Options: nosniff, X-Frame-Options: DENY, X-XSS-Protection, Cache-Control.

**CORS:** Restricted to `app.cors.allowed-origins` configured domains (default `localhost:5173`), no wildcard `*`.

**Database Connection:** `useSSL=true&requireSSL=true&verifyServerCertificate=true` — database transport encryption.

### 5.6 Account Lockout & Password Policy

**System Users (AuthService):** 5 failed logins → 15-minute lockout. Reset on success.

**Patients (PatientAuthController):** Same 5-failure/15-minute lockout policy.

**Password Complexity (@ValidPassword):** Minimum 8 chars + uppercase + lowercase + digit + special character.

**Password History:** `password_history` table stores last 3 password BCrypt hashes; new passwords cannot match history.

**Token Config Externalized:** JWT expiry controlled by `app.security.access-token-expiry-seconds` (default 7200s), not hardcoded.

---

## Layer 6: Encryption — AesCryptoUtil + JPA Converter

### 6.1 Architecture Design

```
application.yml
  app.aes.key: ${AES_KEY:...}
       │ @Value
       ▼
AesCryptoUtil (@Component)              AesAttributeConverter (@Converter)
  @PostConstruct init()    ──static──▶   convertToDatabaseColumn()
  static encrypt(String)      bridge     └── AesCryptoUtil.encrypt()
  static decrypt(String)    ◀──static──  convertToEntityAttribute()
       │                    bridge       └── AesCryptoUtil.decrypt()
       │
       ▼
  Decryption failure → log.error + return "[DECRYPT_FAILED]"
  No exception thrown — one bad row won't crash entire JPA query
```

**Why is the static bridge needed?** JPA instantiates `AttributeConverter` via `new`, bypassing the Spring container, so `@Value` cannot be directly injected into the Converter. Solution: Spring `@Component` holds the key, Converter accesses it via static methods.

### 6.2 Encrypted Field Inventory

| Entity | Encrypted Fields (AesAttributeConverter) | Count |
|--------|---------|-------|
| Patient | `ssn`, `name`, `primaryCareProvider`, `phoneMobile`, `phoneHome`, `phoneWork`, `email`, `addressLine1`, `addressLine2`, `city`, `state`, `zipCode`, `emergencyContactName`, `emergencyContactPhone`, `insurancePayer`, `insuranceMemberId`, `insuranceGroupNumber`, `medicalHistory`, `allergies` (+ `dateOfBirth` via LocalDateAttributeConverter) | 19+1 |
| SysUser | `phone`, `email`, `stateLicenseNumber`, `deaNumber` | 4 |
| Message | `content` (chat records) | 1 |
| Bill | `insuranceClaimNumber` | 1 |
| Prescription | `deaNumber` | 1 |
| **Total** | | **26+1** |

### 6.3 Encryption Algorithm

**AES-256-GCM / NoPadding** — GCM mode provides authenticated encryption (confidentiality + integrity), random 12-byte IV per encryption, 128-bit authentication tag prevents ciphertext tampering.

### 6.4 Key Versioning & Rotation

Ciphertext format upgraded from `[IV:12B][ciphertext+tag]` to `[version:1B][IV:12B][ciphertext+tag]`, supporting key rotation:

| Version Byte | Key | Description |
|-------------|-----|-------------|
| `0x01` | `app.aes.key` (current) | All new encryptions use this version |
| No prefix | `app.aes.key.previous` → fallback to `app.aes.key` | Legacy data backward-compatible decryption |

**Rotation Process (zero-downtime API):**
1. Admin generates a new key externally (e.g. `openssl rand -base64 32`)
2. `POST /api/v1/admin/keys/rotate { "newKey": "<new key>", "oldKey": "<current app.aes.key>" }`
3. `AesCryptoUtil.rotate()` installs the new key as CURRENT, old key as PREVIOUS, activates rotation
4. `KeyRotationService` starts async background migration — scans all 27 encrypted columns for rows whose ciphertext does NOT start with `01` (legacy key), decrypts with previous key, re-encrypts with current key
5. Monitor progress via `GET /api/v1/admin/keys/rotation-status`
6. Once `rotationActive=false` and `complete=true`, update `application.yml` (`app.aes.key` → new value, `app.aes.key.previous` → old value) to survive restarts

On restart with `app.aes.key.previous` still set, `@PostConstruct` triggers an idempotent scan — no rows match, completes in under a second.

A daily 3 AM cron safety check idempotently resumes any incomplete migration.

**Key Audit:** `AesCryptoUtil.init()` auto-writes `KEY_INIT` / `KEY_ROTATION` events to the `key_audit` table. `KeyRotationService` writes `ROTATION_COMPLETE` on successful migration. ADMIN can view key lifecycle via `GET /api/v1/admin/keys/history` and migration progress via `GET /api/v1/admin/keys/rotation-status`.

### 6.5 Encryption Algorithm & Key Rotation Details

> Full encrypted field inventory in §6.2. This section focuses on algorithm details.
>
> Patient entity's `dateOfBirth` uses a separate `LocalDateAttributeConverter`: converts LocalDate to ISO string then AES-256-GCM encrypts, storing as VARCHAR(100). All other encrypted fields uniformly use `AesAttributeConverter`.

---

## Layer 7: Async Audit Logging — AOP + @Async

### 7.1 Architecture

```
Business Thread                         auditExecutor Thread Pool
─────────────                           ────────────────────────
@Transactional                           @Transactional(REQUIRES_NEW)
PatientService.create() ─commit→         AuditLogWriter.writeAsync()
    │                                          │
    ▼                                          ▼
AuditLogAspect.audit()                  auditLogRepository.save()
  Captures: userId, username, IP,       Independent tx, 3s timeout
  targetId, detail                     Failure → log.error
  phiAccess? → params masked as [PHI]   Does NOT affect business tx ✓
```

### 7.2 Key Annotation

```java
@Auditable(module = "patient", action = "CREATE", phiAccess = true)
```

| Attribute | Purpose |
|-----------|---------|
| `module` | Business module (patient/appointment/prescription/billing/system/export) |
| `action` | Operation type (CREATE/UPDATE/DELETE/EXPORT_PATIENTS/PAY/ADJUDICATE...) |
| `phiAccess` | When true, parameter values are replaced with `[PHI]` in audit log, preventing ePHI secondary disclosure |

### 7.3 Thread Pool Config

```java
@Bean(name = "auditExecutor")
public Executor auditExecutor() {
    // corePoolSize=2, maxPoolSize=4, queueCapacity=500
    // Queue full → CallerRunsPolicy (synchronous execution, never drop logs)
}
```

### 7.4 Audit Log Query API

`GET /api/v1/audit-logs` (ADMIN only) — multi-condition dynamic query + pagination:

| Parameter | Description |
|-----------|-------------|
| `userId` / `patientId` | Filter by user/patient |
| `module` / `action` | Filter by business module/operation type |
| `fromDate` / `toDate` | Date range filter |
| `page` / `size` | Pagination (default 1/20) |

Backed by `JpaSpecificationExecutor` + dynamic `Predicate` composition.

### 7.5 Audit Coverage

`@Auditable` annotation covers: PatientService (CRUD), AppointmentService (CRUD), PrescriptionService (CRUD), BillService (lifecycle), SysUserService (CRUD), ExportController (CSV exports), PatientPortalController (self-service export, profile update).

---

## Layer 8: Patient Auth Separation — PatientAuth

### 8.1 Why Separate?

HIPAA requires medical record access and authentication records to be audited separately. Moving `username`/`password` out of the `patient` table:

```
Before:                          After:
┌─────────────────────┐          ┌─────────────────────┐
│ Patient             │          │ Patient             │
│  - username ❌      │          │  - name, mrn, ssn   │
│  - password ❌      │          │  - dateOfBirth ...  │
│  - medicalHistory   │          └─────────┬───────────┘
└─────────────────────┘                    │ patient_id (FK)
                                  ┌─────────▼───────────┐
                                  │ PatientAuth         │
                                  │  - username ✅      │
                                  │  - password (BCrypt)│
                                  │  - failedAttempts   │
                                  │  - lockedUntil      │
                                  └─────────────────────┘
```

### 8.2 Account Lockout

5 failed logins → 15-minute lockout. `isLocked()` checks `lockedUntil > now`.

---

## Layer 9: FHIR Interoperability — HAPI FHIR Native Types

### 9.1 Architecture

Migrated from custom DTOs to `org.hl7.fhir.r4.model.*` (HAPI FHIR 7.4), gaining schema validation, standard serialization (JSON/XML), and US Core extension support.

### 9.2 Endpoints

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/fhir/metadata` | **CapabilityStatement** — FHIR 4.0.1 + SMART on FHIR security + OAuth2 URIs |
| `GET /api/v1/fhir/Patient/{id}` | Single FHIR Patient resource (SSN masked to last-4) |
| `GET /api/v1/fhir/Patient?_id={id}` | FHIR search returning Bundle |
| `GET /api/v1/fhir/Observation/{id}` | Single FHIR Observation with interpretation Coding |
| `GET /api/v1/fhir/Observation?patient=&code=` | Observation search by patient + LOINC code |
| `GET /api/v1/patients/{id}/case` | Complete clinical summary **Bundle** — Patient + Condition + AllergyIntolerance + Encounter[] + MedicationRequest[] |

### 9.3 SMART on FHIR

CapabilityStatement declares `SMART-on-FHIR` security service. JWT `scp` claims contain FHIR scopes by role:

| Role | FHIR Scopes |
|------|------------|
| ADMIN / DOCTOR | `patient/*.read`, `patient/*.write`, `user/*.read`, `system/*.read` |
| PATIENT | `patient/Patient.read`, `patient/Observation.read` |

### 9.4 US Core Profile Correction

Race/ethnicity extensions corrected from plain `StringType` to US Core structured format:

```
Extension (us-core-race)
  ├── extension[ombCategory]: Coding (urn:oid:2.16.840.1.113883.6.238)
  │     └── code: "2106-3" (White) / "2028-9" (Asian) / "2054-5" (Black) / ...
  └── extension[text]: String
```

OMB code mapping: White→2106-3, Black→2054-5, Asian→2028-9, American Indian→1002-5, Hawaiian/PI→2076-8, Hispanic→2135-2, Not Hispanic→2186-5.

### 9.5 FHIR Coding

| Coding System | Purpose |
|--------------|---------|
| `http://hl7.org/fhir/sid/us-ssn` | SSN identifier (masked last-4) |
| `http://hl7.org/fhir/sid/us-mrn` | MRN identifier |
| `http://hl7.org/fhir/sid/ndc` | FDA National Drug Code |
| `http://www.nlm.nih.gov/research/umls/rxnorm` | RxNorm concept code |
| `http://loinc.org` | LOINC lab test coding |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-race` | US Core Race extension (ombCategory + text) |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity` | US Core Ethnicity extension |
| `http://terminology.hl7.org/CodeSystem/restful-security-service` | SMART on FHIR security service |
| `urn:oid:2.16.840.1.113883.6.238` | OMB Race/Ethnicity category coding system |

---

## Layer 10: US Business Module Domains

### 10.1 Patient

| Old Field (Non-US) | New Field (US) |
|--------------------|----------------|
| `id_card` | `mrn` + `ssn` |
| `age` | `dateOfBirth` |
| `gender` (0/1) | `sexAtBirth` (M/F/U) + `genderIdentity` |
| `phone` | `phoneMobile` + `phoneHome` + `phoneWork` |
| `address` (single string) | `addressLine1` + `addressLine2` + `city` + `state` + `zipCode` |
| — | `race` (OMB), `ethnicity`, `preferredLanguage` |
| — | `insurancePayer`, `insuranceMemberId`, `insuranceGroupNumber` |
| — | `emergencyContactName/Phone/Relation` |
| — | `primaryCareProvider`, `patientStatus`, `maritalStatus` |

### 10.2 Provider (SysUser)

New fields: NPI (10-digit) / DEA Number / State License + State / Taxonomy Code / Credentials (MD/DO/NP) / Specialty.

### 10.3 Appointment

| New Field | Value Domain |
|-----------|-------------|
| `visitType` | NEW_PATIENT / FOLLOW_UP / ANNUAL_PHYSICAL / URGENT_CARE / CONSULTATION |
| `chiefComplaint` | Chief complaint (reason for visit) |
| `department` | Cardiology / Family Medicine / Allergy & Immunology |
| `duration` | Appointment duration (minutes) |
| `cptCode` | CPT E&M code (99201-99499) |
| `status` | **0=scheduled, 1=arrived, 2=cancelled, 3=completed, 4=no-show, 5=rescheduled, 6=in-progress** |

### 10.4 Prescription

| New Field | Purpose |
|-----------|---------|
| `ndcCode` / `rxnormCode` | FDA + NIH drug codes (required for eRx) |
| `refills` / `daysSupply` | Refill count / days supply |
| `daw` | Dispense As Written (0=substitution allowed, 1=dispense as written) |
| `route` | Route of administration (PO/IV/IM/INH/SL) |
| `sig` | Complete sig instructions text |
| `icd10Codes` | ICD-10-CM diagnosis codes |
| `prescriberNpi` / `deaNumber` / `controlledSchedule` | Prescriber NPI + DEA + controlled schedule |
| `pharmacyName/Phone/Npi` | Dispensing pharmacy |
| `prescriptionType` | MEDICATION / LAB / PROCEDURE / DME |
| `rxStatus` | active / completed / discontinued / on-hold |

### 10.5 Billing

| New Field | Purpose |
|-----------|---------|
| `claimStatus` | DRAFT → SUBMITTED → PENDING → PAID / DENIED → APPEALED |
| `totalCharge` / `insuranceAdjustment` / `insurancePayment` / `patientResponsibility` | Insurance claim calculation |
| `cptCodes` / `icd10Codes` / `placeOfServiceCode` | Coding |
| `billingProviderNpi` / `renderingProviderNpi` | Billing/rendering provider NPI |
| `insuranceClaimNumber` (encrypted) / `priorAuthorizationNumber` | Claim # / prior auth |
| `paymentMethod` / `receiptNumber` | Payment method / receipt # |

BillService provides complete claim lifecycle: `submitClaim()` → `adjudicate()` → `pay()` / `denyClaim()`.

### 10.6 CDS — Clinical Decision Support

**Drug-Drug Interaction:** `drug_interaction` table stores drug pair interaction rules. `CdsService.checkDrugInteractions()` performs pairwise cross-check on all prescription items, returning severity (contraindicated/severe/moderate/minor) + description + recommendation.

**Drug-Allergy Contraindication:** `drug_allergy_class` table maps RxNorm codes to allergy classes. `CdsService.checkAllergyContraindications()` cross-references patient allergies with prescription drugs, flagging contraindications.

**CDS Endpoint:** `POST /api/v1/cds/check` allows pre-prescription screening. `PrescriptionService.create()` has built-in CDS checks — warnings are logged but do not hard-block.

### 10.7 Integration Engine

**Architecture:** Hospital EHR → HL7 v2 → Mirth Connect → JSON/HTTP → Our Backend. The backend does NOT parse HL7 v2 pipe messages.

**ADT Events:** `POST /api/v1/integration/adt` — receives A01 (admit)/A03 (discharge)/A08 (update), auto-upserts Patient by MRN.

**Lab Results:** `POST /api/v1/integration/lab-results` — batch writes to `observation` table, `sourceMessageId` idempotent dedup. Supports one-way conversion from `Observation` table to FHIR Observation resource.

### 10.8 LOINC Lab Coding

`loinc_catalog` table stores 29 common LOINC codes (CBC/BMP/Lipid/HbA1c/TSH/UA), each with unit, reference range, panel_parent_code.

`LabAnalysisService.autoFlag()` five-level abnormal flagging: LL (<80% lower limit)/L/H/HH (>150% upper limit)/N.

Trend query: `GET /api/v1/patients/{id}/observations?loinc=CODE`. Panel expansion: `GET /api/v1/loinc/panel/CBC`.

### 10.9 ePrescribing + EPCS

`pharmacy_directory` table stores pharmacy NPI + address + EPCS support flag.

`PUT /api/v1/prescriptions/{id}/transmit?pharmacyId=` — generates NCPDP SCRIPT 10.6 NewRx XML. Controlled substances (Schedule II-V) trigger `EpcsService` EPCS audit (records prescriber NPI + DEA).

### 10.10 eCQM — Clinical Quality Measures

`quality_measure` table stores CMS MIPS/MACRA measure definitions (title + SQL query). `QualityMeasureService.calculateReport()` executes denominator/numerator/exclusion SQL → performance rate → compare against CMS targets.

Seed data includes 3 CMS measures: CMS122v11 (HbA1c control), CMS125v11 (breast cancer screening), CMS165v11 (hypertension control).

---

## Layer 11: Infrastructure

### 11.1 BaseEntity — Common Parent Class

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer isDeleted;

    @Version
    private Integer version;  // Optimistic locking

    @PrePersist  protected void onCreate() { ... }
    @PreUpdate   protected void onUpdate() { ... }
}
```

All entities extend it, auto-acquiring id, timestamps, soft delete, and optimistic locking.

### 11.2 Unified Response Format

```java
// Success
Result.ok(data)  → { "code": 200, "message": "ok", "data": {...} }

// Failure (correct HTTP status code)
Result.fail(404, "Patient not found")
  → HTTP 404 + { "code": 404, "message": "Patient not found" }
```

`GlobalExceptionHandler` maps `BusinessException` codes to correct HTTP status codes (401/403/404/409/400).

### 11.3 Rate Limiting

| Endpoint | Limit |
|----------|-------|
| `/api/v1/auth/login` | 10/min/IP |
| `/api/v1/patient/login` | 10/min/IP |
| `/api/v1/export/*` | 5/hour/IP |

Based on Redisson `RRateLimiter`, exceeded limits return HTTP 429.

### 11.4 Caching

Patient ePHI is **NOT cached** in Redis. Only cached:
- Dashboard statistics (`@Cacheable("dashboard")`)
- SysUser basic info (roles/menus)

**Redis PHI Protection:** `PhiMaskingRedisSerializer` auto-detects `@PhiField` annotations during serialization and replaces marked fields with `[PHI-REDACTED]` — defense in depth ensures no plaintext PHI leakage even if a PHI-containing object is accidentally cached.

### 11.5 Data Retention

`DataRetentionJob` (`@Scheduled cron="0 0 3 * * ?"`) runs daily at 3 AM to purge expired audit logs:
- `app.retention.audit-log-days=2190` (6-year HIPAA requirement)
- Soft-deleted records retained for `app.retention.soft-delete-days=365` days

### 11.6 Consent Management

`POST /api/v1/consent` (ADMIN) + `GET /api/v1/patient/me/consent` (PATIENT). Supports consent_type (OPT_IN/OPT_OUT/TREATMENT/RESEARCH) + scope + revoke. Supports HIPAA §164.508.

### 11.7 Emergency Access (Break-Glass)

`POST /api/v1/emergency/access/{patientId}?reason=...` — provider can break through normal access controls to view any patient data, 30-minute auto-expiry. Synchronously audited (`EmergencyAccess.audited=1`) + WARN-level logging. ADMIN can view history via `GET /api/v1/emergency/history`.

### 11.8 Structured Logging

`logback-spring.xml`:
- **Production** — JSON format (SIEM-friendly), separate `logs/audit.log` (90-day retention)
- **Development** — Human-readable format
- Hibernate SQL logging off (`WARN` level)

### 11.9 Testing

137 tests across 5 files:
- `IntegrationTest` — 112 integration tests covering all 14 business module API endpoints
- `PatientAuthControllerTest` — 12 tests: login success/disabled/locked/bad-password/patient-orphaned, token expiry config, audit resilience, user enumeration prevention
- `AesAttributeConverterTest` — 8 tests: encrypt/decrypt roundtrip, null handling, random IV, corrupt data degradation, reencrypt (legacy upgrade + edge cases)
- `GlobalExceptionHandlerTest` — 3 tests: 401/404/409 status code mapping
- `BaseEntityTest` — 2 tests: `@Version` field + `@PrePersist` callback

---

## Layer 12: Complete Request Lifecycle

Using `admin` accessing the patient list as an example:

```
1. Filter Layer
   - CorsFilter: validates Origin
   - RateLimiter: non-login endpoints have no rate limit

2. Spring Security Layer
   - Extracts "Bearer <access_token>" from Header
   - Production: Okta JWKS public key validates signature and expiry
   - Development: SecurityConfigDev local HMAC validation
   - JwtClaimMapper: groups=["ADMIN"] → ROLE_ADMIN
   - @PreAuthorize("hasAnyRole('ADMIN','DOCTOR')") → passes ✓

3. Controller Layer
   - PatientController.page(1, 10, "Anderson")

4. Service Layer
   - PatientService.page()
   - Specification: mrn LIKE '%Anderson%' OR email LIKE '%Anderson%'
   - Note: name and phone are encrypted, cannot be searched via LIKE
   - patientRepository.findAll(spec, pageable)

5. Repository Layer
   - JPA generates SQL:
     SELECT * FROM patient
     WHERE is_deleted=0 AND version IS NOT NULL
     AND (mrn LIKE '%Anderson%' OR email LIKE '%Anderson%')
     LIMIT 10 OFFSET 0
   - @SQLRestriction("is_deleted = 0") auto-applied
   - AesAttributeConverter auto-decrypts name, ssn, phoneMobile, etc.

6. Response
   - Entity → PatientVO.fromEntity()
   - SSN shows only last-4 (maskLast4)
   - PageResult + Result wrapping
   - Jackson serializes JSON
```

---

## Summary: Design Highlights

| Highlight | Implementation |
|-----------|---------------|
| **HIPAA Encryption** | AES-256-GCM + JPA @Convert transparent encryption + versioned key rotation + degradation |
| **HIPAA Auditing** | Async AOP (REQUIRES_NEW tx) + PHI masking + dedicated thread pool + ADMIN query API |
| **Okta OAuth2** | Production JWKS validation + dev local fallback + token refresh rotation + SMART scopes |
| **FHIR Standard** | HAPI FHIR native types + CapabilityStatement + SMART on FHIR + US Core structured extensions |
| **US Medical Compliance** | 5 modules with US fields (NPI/NDC/CPT/ICD-10/POS/USCDI demographics) |
| **Soft Delete** | @SQLDelete + @SQLRestriction + scheduled retention purge |
| **Optimistic Locking** | @Version on BaseEntity |
| **Account Security** | BCrypt + login rate limiting + dual lockout (system users + patients) + password complexity + history policy + HSTS/Clickjack/XSS headers |
| **Data Minimization** | VO SSN last-4 + FHIR SSN masking + CSV PHI masking + audit PHI masking + Redis @PhiField redaction |
| **Break-Glass** | Emergency access + synchronous audit + 30-minute auto-expiry |
| **Consent** | Consent entity + patient self-service view + revoke support |
| **Key Security** | PBKDF2 310k iterations + versioned ciphertext + key rotation + key_audit audit table |
| **21 CFR Part 11** | Login success/failure auditing + SHA-256 row_hash tamper detection + role/menu change auditing |
| **Anti-DoS** | FHIR pagination (max=500) + CSV StreamingResponseBody + token refresh rate limiting + RestTemplate connection pooling |
| **Layered Decoupling** | Entity/DTO separation + static bridge for JPA/Spring DI conflict |
| **Ready to Run** | DataInitializer JPA writes + US synthetic seed data |
