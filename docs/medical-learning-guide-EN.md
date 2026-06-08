# Traditional Backend Engineers → Healthcare Domain: Key Learning Modules

> Based on the `medical-project` codebase, for engineers with traditional backend experience transitioning to the US healthcare domain.

---

## Priority 1: Must Master (Used Daily)

### 1. PHI Encryption System

**Files:** `common/config/AesCryptoUtil.java` + `AesAttributeConverter.java`

This is the **biggest difference** between healthcare backends and standard backends. Regular backends only need password encryption; healthcare requires encryption of nearly all personal data.

| Item | Description |
|------|-------------|
| Algorithm | **AES-256-GCM / NoPadding** (authenticated encryption — GCM provides both confidentiality and integrity) |
| IV | Random 12-byte IV generated per encryption |
| Auth Tag | 128-bit, prevents ciphertext tampering |
| Key Derivation | `app.aes.key` → PBKDF2-HMAC-SHA256 (310k iterations) → 256-bit AES Key |

**Transparent Encryption Mechanism:**

```
Business code reads/writes fields (plain String)
       ↕
JPA @Convert(converter = AesAttributeConverter.class)
       ↕
AesCryptoUtil.encrypt() / decrypt()
       ↕
Database stores hex-encoded AES-GCM ciphertext
```

The business layer is completely unaware — a patient's `name` is just a plain String in code. The JPA layer handles encryption/decryption automatically.

**Static Bridge Design (Why it's needed):**

JPA instantiates `AttributeConverter` via `new`, bypassing Spring's container, so `@Value` cannot be directly injected into the Converter.

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
  No exception thrown — one bad row won't crash the entire JPA query
```

**Degradation Strategy:** Decryption failures return `[DECRYPT_FAILED]` instead of throwing. This is a critical design decision — a single corrupted row (e.g., old-format data during key rotation) should not crash an entire query.

**Encrypted Field Inventory:**

| Entity | Encrypted Fields | Reason |
|--------|------------------|--------|
| Patient | `ssn`, `name`, `primaryCareProvider`, `phoneMobile`, `phoneHome`, `phoneWork`, `email`, `addressLine1`, `addressLine2`, `city`, `state`, `zipCode`, `emergencyContactName`, `emergencyContactPhone`, `insurancePayer`, `insuranceMemberId`, `insuranceGroupNumber`, `medicalHistory`, `allergies` (+ `dateOfBirth` via LocalDateAttributeConverter) | HIPAA Protected Health Information |
| SysUser | `phone`, `email`, `stateLicenseNumber`, `deaNumber` | Provider sensitive information |
| Message | `content` | Chat records may contain medical discussions |
| Bill | `insuranceClaimNumber` | Claim number links to patient |
| Prescription | `deaNumber` | Controlled substance prescriber identifier |

**Key Rotation Mechanism:**

Ciphertext format: `[version:1B][IV:12B][ciphertext+tag]`. Version byte `0x01` indicates current key. Data without a version prefix auto-falls back for decryption.

```
Rotation Steps:
1. Update app.aes.key (new key) → new encryptions use v1 prefix
2. Old key set as app.aes.key.previous → old data remains decryptable
3. Natural business writes → old data gradually re-encrypted to v1
4. Confirm no old data → remove app.aes.key.previous
```

`AesCryptoUtil.init()` auto-writes audit events to the `key_audit` table on startup.

---

### 2. HIPAA Audit Logging

**Files:** `common/audit/AuditLogAspect.java` + `AuditLogWriter.java` + `AuditLog.java`

HIPAA Security Rule §164.312(b) requires recording **who, when, on which patient, performed what operation**.

**Architecture:**

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

**Key Design Decisions:**

| Decision | Reason |
|----------|--------|
| Async writes (`@Async`) | Audit does not block business response |
| Independent transaction (`REQUIRES_NEW`) | Audit failure does not roll back business transaction |
| CallerRunsPolicy rejection | Sync execution when thread pool full — never silently drop logs |
| PHI masking | `phiAccess=true` → method parameter values replaced with `[PHI]`, prevents sensitive data leakage in audit trail |
| Structured JSON logging (prod) | SIEM / log aggregator friendly |
| `audit.log` separate file, 90-day retention | Meets audit retention requirements |

**`@Auditable` Annotation Parameters:**

```java
@Auditable(module = "patient", action = "CREATE", phiAccess = true)
```

| Attribute | Description | Example |
|-----------|-------------|---------|
| `module` | Business module | patient / appointment / prescription / billing / system / export |
| `action` | Operation type | CREATE / UPDATE / DELETE / SUBMIT / ADJUDICATE / PAY / DENY / EXPORT |
| `phiAccess` | Whether PHI is involved | true → parameter values replaced with `[PHI]` |

---

### 3. US Medical Data Model

**Files:** `module/patient/entity/Patient.java`, `module/appointment/entity/Appointment.java`, `module/prescription/entity/Prescription.java`, `module/billing/entity/Bill.java`, `module/system/entity/SysUser.java`

Completely different field structures from non-US systems — this is the most important retraining area during transition.

#### 3.1 Patient

| US Field | Type | Description | Non-US Equivalent |
|----------|------|-------------|-------------------|
| `mrn` | VARCHAR(50) UNIQUE | Medical Record Number — internal patient identifier | Chart number |
| `ssn` | VARCHAR(200) encrypted | Social Security Number | National ID |
| `name` | VARCHAR(200) encrypted | Patient name (encrypted at rest) | Name |
| `dateOfBirth` | LocalDate (encrypted) | Date of birth (age computed, not stored) | Age (computed from DOB) |
| `sexAtBirth` | CHAR(1) | M / F / U (biological sex) | Gender 0/1 |
| `genderIdentity` | VARCHAR(50) | Self-identified gender (Male/Female/Non-binary/Transgender...) | — |
| `race` | VARCHAR(100) | OMB 5 categories (White/Black/Asian/AI.AN/NH.PI) | — |
| `ethnicity` | VARCHAR(50) | Hispanic or Latino / Not Hispanic or Latino | — |
| `preferredLanguage` | VARCHAR(10) | Preferred language (en/es/zh...) | — |
| `maritalStatus` | VARCHAR(20) | Marital status (Single/Married/Divorced/Widowed) | — |
| `addressLine1/2` | VARCHAR(100) | Structured address line1 + line2 | Address (single string) |
| `city/state/zipCode` | VARCHAR(50)/CHAR(2)/VARCHAR(10) | City/State (2-letter)/ZIP Code | — |
| `phoneMobile/Home/Work` | VARCHAR(200) encrypted | Three phone types stored separately | Phone (single field) |
| `insurancePayer` | VARCHAR(100) | Insurance company name | Social insurance |
| `insuranceMemberId` | VARCHAR(200) encrypted | Insurance member ID | — |
| `insuranceGroupNumber` | VARCHAR(50) | Insurance group number | — |
| `emergencyContactName/Phone/Relation` | — | Emergency contact | — |
| `primaryCareProvider` | VARCHAR(100) | Primary Care Provider (PCP) name | — |
| `patientStatus` | VARCHAR(20) | active / inactive / deceased | — |
| `medicalHistory` | VARCHAR(4000) | Past medical history | Medical history |
| `allergies` | VARCHAR(2000) | Allergies (penicillin/shellfish...) | Allergies |

#### 3.2 Provider (SysUser)

| US Field | Description |
|----------|-------------|
| `npi` (10-digit) | National Provider Identifier — unique nationwide provider ID |
| `deaNumber` | DEA registration number (controlled substance prescribing authority), encrypted |
| `stateLicenseNumber` | State medical license number, encrypted |
| `licenseState` | Licensing state (2-letter, e.g., "IL") |
| `taxonomyCode` | Provider taxonomy code (e.g., "207Q00000X" = Family Medicine MD) |
| `credentials` | Credential abbreviation (MD/DO/NP/PA) |
| `specialty` | Specialty ("Family Medicine" / "Cardiology") |

#### 3.3 Appointment

| US Field | Values / Description |
|----------|---------------------|
| `visitType` | NEW_PATIENT / FOLLOW_UP / ANNUAL_PHYSICAL / URGENT_CARE / CONSULTATION |
| `chiefComplaint` | Chief complaint — reason for visit |
| `department` | Cardiology / Family Medicine / Allergy & Immunology / ... |
| `duration` | Appointment duration (minutes) |
| `cptCode` | CPT Evaluation & Management code (99201-99499) |
| `status` | **0=scheduled, 1=arrived, 2=cancelled, 3=completed, 4=no-show, 5=rescheduled, 6=in-progress** |
| `checkInTime / checkOutTime` | Actual check-in/check-out times |

#### 3.4 Prescription

| US Field | Description |
|----------|-------------|
| `ndcCode` | FDA National Drug Code (e.g., "65862-0017-01") |
| `rxnormCode` | NIH RxNorm concept code (clinical drug standardization, e.g., "308191") |
| `refills` | Number of refills (0 = no refills) |
| `daysSupply` | Days supply |
| `daw` | Dispense As Written (0=substitution allowed, 1=dispense as written, no generic) |
| `route` | Route of administration (PO=oral / IV=intravenous / IM=intramuscular / INH=inhaled / SL=sublingual) |
| `sig` | Full sig instructions ("Take one capsule three times daily with food") |
| `frequency` | Frequency (QD=daily / BID=twice daily / TID=three times daily / PRN=as needed) |
| `icd10Codes` | ICD-10-CM diagnosis codes (e.g., "J06.9" = acute URI) |
| `prescriberNpi` | Prescribing provider NPI |
| `deaNumber` | DEA number for controlled substance prescribing (encrypted) |
| `controlledSchedule` | Controlled substance schedule (II/III/IV/V — Schedule I cannot be prescribed) |
| `pharmacyName/Phone/Npi` | Dispensing pharmacy information |
| `prescriptionType` | MEDICATION / LAB / PROCEDURE / DME (Durable Medical Equipment) |
| `rxStatus` | active / completed / discontinued / on-hold |

#### 3.5 Billing — Insurance Claim State Machine

```
DRAFT → SUBMITTED → PENDING → PAID
                    ↘ DENIED → APPEALED
```

| US Field | Description |
|----------|-------------|
| `claimStatus` | DRAFT → SUBMITTED → PENDING / PAID / DENIED |
| `totalCharge` | Total charge amount |
| `insuranceAdjustment` | Insurance adjustment (contractual discount) |
| `insurancePayment` | Insurance payment amount |
| `patientResponsibility` | Patient responsibility (= total - adjustment - insurancePayment) |
| `patientPaidAmount` | Patient paid amount |
| `copayAmount` | Copay (fixed out-of-pocket) |
| `cptCodes` | CPT procedure codes |
| `icd10Codes` | ICD-10 diagnosis codes |
| `placeOfServiceCode` | Place of service code (11=office, 21=inpatient, ...) |
| `billingProviderNpi` | Billing provider NPI |
| `renderingProviderNpi` | Rendering provider NPI |
| `insuranceClaimNumber` | Claim number (encrypted) |
| `priorAuthorizationNumber` | Prior authorization number (required by insurers for certain services) |

**Claim lifecycle methods:** `submitClaim()` → `adjudicate()` → `pay()` / `denyClaim()`

---

## Priority 2: Understand and Be Familiar

### 4. FHIR Interoperability Standard

**Files:** `module/patient/PatientCaseService.java` + `FhirConfig.java`

**Background:** The 21st Century Cures Act requires EHR systems to support FHIR APIs (ONC certification condition). FHIR (Fast Healthcare Interoperability Resources) is HL7's modern healthcare data exchange standard.

**Tech Stack:** HAPI FHIR 7.4 (`org.hl7.fhir.r4`), using native FHIR types directly rather than custom DTOs.

**Endpoints:**

| Endpoint | Output | Description |
|----------|--------|-------------|
| `GET /api/v1/fhir/metadata` | `CapabilityStatement` (JSON) | FHIR 4.0.1 + SMART on FHIR security + OAuth2 URIs |
| `GET /api/v1/fhir/Patient/{id}` | `Patient` (JSON) | Single FHIR Patient resource (SSN masked to last-4) |
| `GET /api/v1/fhir/Patient?_id={id}` | `Bundle` (JSON) | FHIR search |
| `GET /api/v1/fhir/Observation/{id}` | `Observation` (JSON) | Single FHIR Observation with interpretation Coding |
| `GET /api/v1/fhir/Observation?patient=&code=` | `Bundle` (JSON) | Observation search by patient + LOINC code |
| `GET /api/v1/patients/{id}/case` | `Bundle` (JSON) | Complete patient clinical summary |

**SMART on FHIR:** CapabilityStatement declares `SMART-on-FHIR` security service. JWT `scp` claims include `patient/*.read`, `user/*.read`, `system/*.read` FHIR scopes.

**US Core Profile:** Race/ethnicity extensions use structured `ombCategory` Codings (OMB system `urn:oid:2.16.840.1.113883.6.238`) + `text` extension, compliant with US Core specification.

**Bundle Contents:**

```
Bundle
  ├── Patient              — Patient demographics
  ├── Condition[]          — Diagnosis records (from medicalHistory + icd10Codes)
  ├── AllergyIntolerance[] — Allergy records
  ├── Encounter[]          — Visit records (from Appointment)
  └── MedicationRequest[]  — Prescription records (from Prescription + Items)
```

**FHIR Coding Systems:**

| Coding System URI | Purpose |
|-------------------|---------|
| `http://hl7.org/fhir/sid/us-ssn` | SSN identifier |
| `http://hl7.org/fhir/sid/us-mrn` | MRN identifier |
| `http://hl7.org/fhir/sid/ndc` | FDA National Drug Code |
| `http://www.nlm.nih.gov/research/umls/rxnorm` | RxNorm concept code |
| `http://loinc.org` | LOINC lab test codes |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-race` | US Core Race extension (OMB categories) |
| `http://hl7.org/fhir/us/core/StructureDefinition/us-core-ethnicity` | US Core Ethnicity extension |
| `http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation` | Abnormal flag interpretation (H/L/N) |

---

### 5. Okta OAuth2 Authentication + RBAC Authorization

**Files:** `security/JwtClaimMapper.java` + `common/config/SecurityConfig.java` + `SecurityConfigDev.java`

```
Production:                          Development (dev profile):
┌─────────┐                          ┌──────────────────┐
│  Okta   │ ← JWKS endpoint          │ SecurityConfigDev │
│  JWT    │    (RS256 public key)     │ Local JwtDecoder  │
│  MFA    │                           │ + DevJwtEncoder   │
└────┬────┘                           │ HMAC-SHA256       │
     │                                └──────────────────┘
     ▼
┌─────────────┐
│ JwtDecoder  │ ← Spring Boot auto-loads JWKS from issuer-uri
│ Validates   │
│ sig + expiry│
└──────┬──────┘
       ▼
┌──────────────┐
│JwtClaimMapper│ ← Okta claims → Spring Security
│ groups→ROLE_* │   groups → ROLE_ADMIN / ROLE_DOCTOR / ROLE_PATIENT
│ scp→SCOPE_*  │   uid → userId
└──────────────┘
```

**Login Flow:**

1. `POST /api/v1/auth/login` sends username/password
2. AuthService local BCrypt verification that user exists and is not disabled
3. Production → calls Okta `/v1/token` (password grant) to get access_token + refresh_token
4. Development → DevJwtEncoder locally issues HMAC-SHA256 JWT (no Okta needed)
5. Frontend stores token, subsequent requests carry `Authorization: Bearer <token>`
6. Spring Security validates signature via Okta JWKS public key (prod) or local HMAC (dev)
7. JwtClaimMapper extracts `groups` claim → `ROLE_ADMIN/DOCTOR/PATIENT`

**Token Refresh Rotation (Production):**

```
POST /api/v1/auth/refresh { refreshToken }
  → Okta /v1/token (grant_type=refresh_token)
  → Old refresh_token auto-invalidated on Okta side (rotation)
  → Returns new access_token + refresh_token
```

**RBAC Model:** User → Role → Menu/Permission → API Access. Method-level `@PreAuthorize`:

```java
@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")   // Doctor or Admin
@PreAuthorize("hasRole('ADMIN')")               // Admin only
@PreAuthorize("hasRole('PATIENT')")             // Patient portal
```

---

### 6. Soft Delete + Optimistic Locking + Unified Base Class

**File:** `common/base/BaseEntity.java`

All business tables extend `BaseEntity`, auto-acquiring:

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private LocalDateTime createTime;   // @PrePersist auto-set
    private LocalDateTime updateTime;   // @PreUpdate auto-updated
    private Integer isDeleted;          // 0=active, 1=deleted

    @Version
    private Integer version;            // Optimistic locking
}
```

**Two annotations subclasses need:**

```java
@Entity
@SQLDelete(sql = "UPDATE xxx SET is_deleted = 1 WHERE id = ? AND version = ?")
@SQLRestriction("is_deleted = 0")
public class Patient extends BaseEntity { ... }
```

| Mechanism | Purpose | Why Healthcare Needs It |
|-----------|---------|------------------------|
| `@SQLDelete` | `deleteById()` only sets `is_deleted=1`, no physical delete | Audit traceability, data recovery |
| `@SQLRestriction` | All queries auto-append `WHERE is_deleted=0` | Business layer is unaware |
| `@Version` | Version check on update, exception on conflict | Prevents concurrent overwrites (multiple providers editing same patient) |

---

## Priority 3: Good to Know

### 7. Patient Auth Separation (PatientAuth)

**File:** `module/patient/entity/PatientAuth.java` + `PatientAuthController.java`

HIPAA requires authentication records and medical records to be audited separately.

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

**Account Lockout:** System users (AuthService) and patients (PatientAuthController) share the same policy: 5 failed logins → 15-minute lockout. Both `SysUser` and `PatientAuth` have `failedAttempts` and `lockedUntil` fields.

**Password Policy:** `@ValidPassword` annotation enforces 8+ chars, uppercase, lowercase, digit, special character. `password_history` table prohibits reuse of last 3 passwords. `passwordChangedAt` field tracks password change time.

---

### 8. Rate Limiting + Caching Strategy

**Files:** `common/config/RateLimiterConfig.java` + `CacheConfig.java`

| Endpoint | Limit | Technology |
|----------|-------|------------|
| `/api/v1/auth/login` | 10/min/IP | Redisson RRateLimiter |
| `/api/v1/patient/login` | 10/min/IP | Redisson RRateLimiter |
| `/api/v1/export/*` | 5/hour/IP | Redisson RRateLimiter |

Exceeded limits return HTTP 429.

**Caching Strategy (Critical):**

| Cached | NOT Cached |
|--------|-----------|
| Dashboard statistics | Patient ePHI (prevents decrypted data leakage in Redis) |
| SysUser basic info | — |

**Redis PHI Protection:** `PhiMaskingRedisSerializer` auto-detects `@PhiField` annotations during serialization and replaces marked fields with `[PHI-REDACTED]` — even if a PHI-containing object is accidentally cached, no plaintext leaks.

---

### 9. Structured Logging

**File:** `resources/logback-spring.xml`

| Environment | Format | Description |
|-------------|--------|-------------|
| dev/h2 | Human-readable | Console color output |
| prod | **JSON** | SIEM/log aggregator friendly (Elasticsearch / Splunk) |
| prod audit | Separate `logs/audit.log` | 90-day retention, meets HIPAA audit retention |

---

### 10. Data Export Security

**Files:** `module/export/` + `util/CsvUtil.java`

Export patient and billing data as CSV. Security measures:
- `@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")` — authorized roles only
- `@Auditable(phiAccess = true)` — export operations logged in audit trail
- PHI masking: phone shows last-4 only, email shows `j***@domain.com`
- Rate limit 5/hour/IP — prevents bulk data exfiltration
- Patient self-service export: `GET /api/v1/patient/me/export` (HIPAA §164.524 Right of Access)
- Patient self-service profile update: `PUT /api/v1/patient/me` — can modify phone/email/address/emergencyContact; name/DOB/MRN require staff verification (HIPAA §164.526)

### 10b. Audit Log Query

`GET /api/v1/audit-logs` (ADMIN) — multi-condition filtering (userId/patientId/module/action/date range) + pagination. Backed by `JpaSpecificationExecutor` + dynamic `Predicate`.

### 10c. Emergency Access (Break-Glass)

`POST /api/v1/emergency/access/{patientId}` — provider can break through normal access controls to view any patient data, 30-minute auto-expiry, synchronously audited.

### 10d. Consent Management

`consent` table + `POST /api/v1/consent` (ADMIN) + `GET /api/v1/patient/me/consent` (PATIENT). Supports OPT_IN/OPT_OUT/TREATMENT/RESEARCH types + revoke.

### 10e. Data Retention

`DataRetentionJob` periodically purges expired audit logs (`app.retention.audit-log-days=2190` = 6-year HIPAA requirement).

---

## Recommended Reading Order (by Code Dependencies)

```
 1. BaseEntity              ← Understand shared fields across all tables (id/isDeleted/version)
 2. Patient entity          ← First complete business entity, understand US field design
 3. PatientService          ← CRUD + JPA Specification dynamic queries
 4. PatientFormDTO / VO     ← DTO conversion pattern (fromEntity / toEntity)
 5. AesCryptoUtil           ← Encryption algorithm, versioned key rotation, static bridge pattern
 6. AesAttributeConverter   ← How JPA Converter achieves transparent encryption/decryption
 7. @ValidPassword          ← Password complexity validation annotation
 8. AuditLogAspect          ← How AOP intercepts @Auditable methods
 9. AuditLogWriter          ← Async writes + REQUIRES_NEW independent transaction
10. AuditLogController      ← ADMIN audit log query API
11. SecurityConfig          ← Spring Security + OAuth2 + security response headers
12. JwtClaimMapper          ← Okta claims → Spring Security authority mapping
13. AuthService             ← Login + account lockout + token issuance (with FHIR scopes)
14. PatientCaseService      ← FHIR Bundle construction + US Core extensions (FHIR interoperability)
15. FhirPatientController   ← FHIR RESTful endpoints + SMART on FHIR
16. BillService             ← Insurance claim state machine
17. DataRetentionJob        ← Scheduled data retention purge
18. EmergencyAccessController ← Break-Glass emergency access
19. CdsService              ← Drug-Drug Interaction + Drug-Allergy contraindication checking
20. IntegrationController   ← ADT + Lab Results JSON API (Mirth Connect integration)
21. FhirObservationController ← FHIR Observation endpoint + interpretation Coding
22. LabAnalysisService      ← LOINC abnormal auto-flag (LL/L/N/H/HH) + trend queries
23. EpcsService             ← EPCS controlled substance transmission audit
24. NcpdpScriptService      ← NCPDP SCRIPT NewRx XML generation
25. QualityMeasureService   ← eCQM SQL queries + CMS performance rate calculation
26. DataInitializer         ← Seed data covering all modules
```

---

## Key External Knowledge

| Knowledge Domain | Specific Content | Related Modules |
|-----------------|------------------|-----------------|
| **HIPAA Security Rule** | §164.312 Technical Safeguards (encryption/audit/access control) | All |
| **ONC USCDI v3+** | US Core Data for Interoperability (determines what fields Patient has) | Patient |
| **ICD-10-CM** | Diagnosis coding system (J06.9=URI, E11.9=Type 2 DM...) | Prescription, Billing |
| **CPT E&M Codes** | Evaluation & Management billing codes (99213=moderate complexity follow-up...) | Appointment, Billing |
| **NDC / RxNorm** | FDA drug code + NIH clinical drug code (dual system) | Prescription |
| **NPI Registry** | US provider 10-digit unique identifier | SysUser |
| **DEA Controlled Substances** | Controlled substance schedules (Schedule II-V) | Prescription |
| **HL7 FHIR R4** | Modern healthcare data exchange standard | PatientCase, FhirConfig |
| **OAuth2 / OIDC** | Okta authentication protocol | Security |
| **SMART on FHIR** | FHIR + OAuth2 app launch standard | Future extension direction |
