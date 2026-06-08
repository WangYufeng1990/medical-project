# Medical Backend — Interview Prep Guide

> Spring Boot 3.4 + MySQL + Redis + Okta OAuth2 + HAPI FHIR R4
> 19 modules | 22 tables | 121 tests (112 integration + 9 unit) | Evolution across 15 rounds

---

## Table of Contents

1. [Elevator Pitch](#1-elevator-pitch)
2. [Module Overview (19 modules)](#2-module-overview)
3. [6 Core Highlights](#3-6-core-highlights)
4. [Key Technical Decisions & Trade-offs](#4-key-technical-decisions)
5. [Database Design (22 tables)](#5-database-design)
6. [Security Architecture — 6 Layers](#6-security-architecture)
7. [API Endpoint Statistics](#7-api-endpoint-statistics)
8. [Tech Stack](#8-tech-stack)
9. [Common Interview Questions (15 Q&As)](#9-common-interview-questions)
10. [Behavioral Interview Tips](#10-behavioral-interview-tips)

---

## 1. Elevator Pitch

> "I built a HIPAA-compliant medical practice management system from scratch — Spring Boot backend with 21 CFR Part 11 audit trails, AES-256-GCM transparent encryption with versioned key rotation, FHIR R4 interoperability using HAPI FHIR, and clinical decision support for drug-drug interaction and drug-allergy checking. 19 modules, 22 database tables, 121 tests, full RBAC with Okta OAuth2."

---

## 2. Module Overview

### 2.1 Core Clinical Modules

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **patients** | 29-field US medical model CRUD | 19/29 fields AES-256-GCM encrypted (+1 LocalDate encrypted); DTO→Entity→VO with SSN masking to last-4 |
| **appointments** | Scheduling with conflict detection | 30-min conflict window; US visit types (NEW_PATIENT/FOLLOW_UP/URGENT_CARE/etc.); CPT E&M codes |
| **prescriptions** | Rx + line items CRUD | NDC/RxNorm dual coding; DEA controlled substance scheduling; CDS integration |
| **billing** | Insurance claim state machine | 6 states: DRAFT→SUBMITTED→PENDING→PAID/DENIED→APPEALED |
| **chat** | Provider-patient messaging | AES-encrypted message content; bidirectional conversation queries |
| **dashboard** | Aggregate statistics | JdbcTemplate direct SQL; Redis cache 30min TTL |

### 2.2 Auth & Identity

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **auth** | Staff login/refresh/logout | Okta OAuth2 password grant; explicit dev-mode flag (no auto-downgrade); login success/failure audit with 5 failure reason codes |
| **patient-auth** | Patient independent login | Authentication separated from medical records (PatientAuth table); 5-failure→15min atomic lockout |
| **user-profile** | Self-service profile management | @ValidPassword complexity enforced; password_history prevents last-3 reuse |

### 2.3 FHIR Interoperability

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **FHIR Case** | Comprehensive patient Bundle | Patient+Condition+AllergyIntolerance+Encounter+MedicationRequest; US Core OMB Coding extensions |
| **FHIR REST** | Resource-level endpoints | `GET /Patient/{id}` SSN masked; `GET /Observation?patient=&code=` with abnormal flag Coding; SMART on FHIR CapabilityStatement |

### 2.4 Clinical Decision Support

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **CDS** | Drug interaction + allergy screening | Pairwise drug_interaction table lookup; patient allergies × drug_allergy_class matching; advisory-only (does not block); doctor can override |

### 2.5 Integration & Lab

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **integration** | Mirth Connect JSON API | ADT events (A01/A03/A08)→Patient upsert by MRN; Lab Results→batch observation insert + sourceMessageId dedup |
| **LOINC** | Lab coding knowledge base | 29-code catalog (CBC/BMP/Lipid/HbA1c/TSH/UA); autoFlag LL/L/N/H/HH 5-level abnormal classification |
| **ePrescribing** | Electronic prescribing | NCPDP SCRIPT 10.6 NewRx XML generation; EPCS controlled substance dual-factor audit; pharmacy directory (NPI+EPCS support) |

### 2.6 Compliance & Audit

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **audit** | Business operation audit trail | AOP @Auditable; REQUIRES_NEW async transactions; SHA-256 row_hash tamper detection; ADMIN query API |
| **consent** | HIPAA §164.508 consent management | CRUD+revoke; patient self-service view |
| **emergency** | Break-glass access | 30-min auto-expiry; synchronous audit (audited=1); WARN-level logging |
| **key-audit** | Key lifecycle audit | KEY_INIT/KEY_ROTATION event recording; ADMIN history query |

### 2.7 Other

| Module | Key Capabilities | Tech Highlights |
|--------|-----------------|-----------------|
| **export** | CSV data export | StreamingResponseBody 500/page; PHI auto-masking (phone→last-4, email→j***@domain) |
| **eCQM** | CMS quality measures | CMS122 (HbA1c), CMS125 (Breast Cancer Screening), CMS165 (Hypertension); SQL-based performance rate calculation |
| **data-retention** | Scheduled data lifecycle | @Scheduled cron; audit logs 6-year archive (archived soft-delete) |

---

## 3. 6 Core Highlights

### 3.1 Encryption Architecture

**Full stack:**
```
┌──────────────────────────────────────────────────┐
│ Key Derivation: PBKDF2-HMAC-SHA256, 310,000 iters │
│               + "medical-aes-v2-salt" fixed salt  │
│               NIST SP 800-132 compliant           │
├──────────────────────────────────────────────────┤
│ Algorithm: AES-256-GCM / NoPadding                │
│          GCM = AEAD = Confidentiality + Integrity │
├──────────────────────────────────────────────────┤
│ Ciphertext format: [version:1B][IV:12B][data+N][tag:16B] │
│          → hex-encoded for DB storage             │
│          version=0x01 → current key               │
│          no prefix → try previous key, fallback    │
├──────────────────────────────────────────────────┤
│ JPA Layer: @Convert(converter=AesAttributeConverter) │
│          Business code reads plain String          │
│          Converter auto encrypts/decrypts          │
│          Static bridge pattern (JPA new→needs DI)  │
├──────────────────────────────────────────────────┤
│ Special: LocalDateAttributeConverter              │
│          dateOfBirth encrypted as VARCHAR(100)     │
├──────────────────────────────────────────────────┤
│ Degradation: return "[DECRYPT_FAILED]"            │
│            Don't throw — one bad row shouldn't     │
│            crash entire JPA query                 │
├──────────────────────────────────────────────────┤
│ Cache Safety: PhiMaskingRedisSerializer           │
│            Detects @PhiField → replaces with       │
│            "[PHI-REDACTED]" during serialization  │
└──────────────────────────────────────────────────┘
```

**Encrypted fields (26 fields across 5 entities: Patient(19) + SysUser(4) + Message(1) + Bill(1) + Prescription(1), +1 Patient dateOfBirth via LocalDate encryption):**

| Entity | Encrypted Fields |
|--------|-----------------|
| Patient | name, ssn, phoneMobile, phoneHome, phoneWork, email, addressLine1, addressLine2, city, state, zipCode, emergencyContactName, emergencyContactPhone, insurancePayer, insuranceMemberId, insuranceGroupNumber, primaryCareProvider, medicalHistory, allergies, dateOfBirth |
| SysUser | phone, stateLicenseNumber, deaNumber, email |
| Message | content |
| Bill | insuranceClaimNumber |
| Prescription | deaNumber |

**Key Rotation Flow:**
```
Step 1: New key → app.aes.key, old key → app.aes.key.previous
Step 2: New encryptions auto-use v1 prefix + current key
Step 3: Decrypt checks version byte: v1→current, no prefix→previous (fallback current)
Step 4: Business writes gradually re-encrypt old data to v1
Step 5: Remove app.aes.key.previous once no old-format data remains
Step 6: key_audit table auto-logs KEY_ROTATION event
```

**Interview delivery:**
"I implemented AES-256-GCM with versioned ciphertext for key rotation — each encrypted value is prefixed with a 1-byte version identifier. Encryption is transparent via JPA @Convert. The key derivation uses PBKDF2-HMAC-SHA256 with 310K iterations per NIST SP 800-132. For defense in depth, Redis cache serialization automatically redacts @PhiField-annotated DTO fields to [PHI-REDACTED]. Decryption failures return a placeholder rather than throwing — so one corrupted row doesn't crash the entire JPA query."

### 3.2 Audit Compliance (21 CFR Part 11)

**Architecture:**
```
Business Thread                      auditExecutor (core=2, max=4, queue=500)
─────────────                        ───────────────────────────────────
@Transactional                       @Transactional(REQUIRES_NEW, timeout=3s)
PatientService.create() ──commit→    AuditLogWriter.writeAsync()
    │                                       │
    ├── AuditLogAspect.around()             ├── AuditLog entity
    │   ├── capture userId                  │   ├── SHA-256 row_hash (tamper-evident)
    │   ├── username (SecurityContext)      │   ├── archived=0 (default)
    │   ├── patientId (from params)         │   ├── @PrePersist → computeRowHash()
    │   ├── targetId (from params)          │   └── auditLogRepository.save()
    │   ├── detail (phiAccess? → [PHI])     │
    │   └── IP (HttpServletRequest)         ├── Fail → log.error (business NOT affected)
    │                                       └── Rejection: CallerRunsPolicy (never drop)
    └── return result
```

**21 CFR Part 11 Compliance Matrix:**

| Requirement | Implementation |
|-------------|---------------|
| §11.10(e) Audit trail accuracy | SHA-256 row_hash = hash(userId\|username\|patientId\|module\|action\|targetId\|detail\|ip\|createTime) |
| §11.10(g) Tamper protection | @SQLRestriction("archived=0") + DataRetentionJob soft-delete only |
| §11.300(b) Sequential audit trail | @PrePersist auto-timestamp; audit_log indexed by create_time |
| §11.200 Electronic signatures | EPCS controlled substance transmission audit; architecture ready for dual-factor |

**Audit Coverage:**
- All CRUD operations (Patient, Appointment, Prescription, Bill, SysUser, SysRole, SysMenu)
- Export operations (CSV patients/bills)
- Login operations (LOGIN_SUCCESS + 5 failure reasons)
- Patient self-service (export, profile update)
- Emergency access (synchronous, audited=1)

**Interview delivery:**
"The audit trail is 21 CFR Part 11 compliant. Every row is hashed with SHA-256 for tamper detection — the hash covers all data columns. Audit records are soft-deleted via an archived flag rather than physically removed. Login successes and failures are both audited with reason codes. The audit writer runs on a dedicated thread pool with REQUIRES_NEW transactions, so audit failures never roll back business transactions."

### 3.3 FHIR R4 Interoperability

**Endpoint Matrix:**

| HTTP Method | Path | Returns | Notes |
|-------------|------|---------|-------|
| GET | `/fhir/metadata` | CapabilityStatement | FHIR 4.0.1 + SMART on FHIR security + OAuth2 URIs |
| GET | `/fhir/Patient/{id}` | Patient | SSN masked to ***-**-6789 |
| GET | `/fhir/Patient?_id=` | Bundle | Paginated search (_count=50, max=500) |
| GET | `/fhir/Observation/{id}` | Observation | Interpretation Coding (H/L/N) |
| GET | `/fhir/Observation?patient=&code=` | Bundle | Trend query, _count=100 |
| GET | `/patients/{id}/case` | Bundle | Comprehensive: Patient+Condition+AllergyIntolerance+Encounter[]+MedicationRequest[] |

**Coding Systems:**

| System URI | Purpose | Example Codes |
|------------|---------|---------------|
| `http://hl7.org/fhir/sid/us-ssn` | SSN identifier | — (masked) |
| `http://hl7.org/fhir/sid/us-mrn` | MRN identifier | — |
| `http://hl7.org/fhir/sid/ndc` | FDA National Drug Code | — |
| `http://www.nlm.nih.gov/research/umls/rxnorm` | RxNorm concept | — |
| `http://loinc.org` | LOINC lab codes | 2345-7 (Glucose), 4548-4 (HbA1c) |
| `urn:oid:2.16.840.1.113883.6.238` | OMB race/ethnicity | 2106-3 (White), 2054-5 (Black), 2028-9 (Asian) |
| `http://terminology.hl7.org/CodeSystem/v3-ObservationInterpretation` | Abnormal flag | H (High), L (Low), N (Normal) |

**US Core Extension Fix:**
- **Before:** Race stored as `new StringType("White")` — non-compliant
- **After:** Structured `ombCategory` Coding + `text` extension per US Core IG

**Interview delivery:**
"I built FHIR R4 Patient and Observation RESTful endpoints using HAPI FHIR 7.4. The CapabilityStatement advertises SMART on FHIR with OAuth2 security URIs. Race and ethnicity extensions use proper US Core ombCategory Codings with OMB system URIs — not plain string types. SSN is masked to last-4 in all FHIR outputs per HIPAA minimum necessary."

### 3.4 Clinical Decision Support (CDS)

**Interaction Check Flow:**
```
POST /api/v1/cds/check or PrescriptionService.create()
  │
  ├── 1. Save prescription + line items
  │
  ├── 2. CdsService.checkDrugInteractions()
  │     └── For each pair (item[i], item[j]):
  │           drug_interaction table lookup (bidirectional)
  │           → severity: contraindicated / severe / moderate / minor
  │           → description: pharmacologic mechanism
  │           → recommendation: clinical guidance
  │
  ├── 3. CdsService.checkAllergyContraindications()
  │     └── Patient allergies × drug_allergy_class mapping
  │           → allergy class match (e.g., Penicillin → Amoxicillin)
  │           → returns contraindicated
  │
  └── 4. Result: advisory only (WARNING, not BLOCK)
        Doctor can override with documented reasoning
```

**Seed Data Examples:**
```
Metformin(6809) + Ibuprofen(5640) = moderate
  "NSAIDs may reduce antihyperglycemic effect and increase lactic acidosis risk"
  → Recommendation: "Monitor blood glucose. Consider acetaminophen."

Amoxicillin(308191) → Penicillin allergy class = contraindicated
  "Patient has known Penicillin allergy. Amoxicillin is contraindicated."
  → Recommendation: "Consider alternative. Document override if essential."
```

**Interview delivery:**
"When a prescription is created, the CDS engine automatically runs pairwise drug-drug interaction checks and drug-allergy contraindication screening. The interaction rules table is bidirectional and stores severity, mechanism, and clinical recommendations. CDS warnings are advisory only — they don't block prescribing. The doctor can override with documented reasoning, which is the clinically appropriate approach."

### 3.5 Account Security

**4-Layer Protection:**

| Layer | Mechanism | Implementation Detail |
|-------|-----------|----------------------|
| Password Policy | @ValidPassword | 8+ chars, upper/lower/digit/special; password_history table blocks last-3 reuse |
| Account Lockout | Atomic JPQL | 5 failures→15min lock; `@Modifying UPDATE SET failed_attempts = COALESCE(failed_attempts,0)+1` — no race condition |
| Auth Strategy | Explicit dev-mode flag | `app.security.dev-mode=true` must be set; missing Okta config → hard failure (no silent downgrade) |
| Rate Limiting | Redisson RRateLimiter | login:10/min, refresh:20/min, export:5/hour |

**Dev-mode Security Evolution:**
```
Before: isDevMode = isBlank(clientId) || isBlank(issuerUri)
        → Missing Okta in production → silently downgraded to local JWT = SECURITY HOLE

After:  @Value("${app.security.dev-mode:false}") private boolean devMode;
        → Must explicitly set app.security.dev-mode=true
        → @Profile({"dev","h2"}) scopes SecurityConfigDev
        → Missing Okta → hard startup failure
```

**Interview delivery:**
"I implemented atomic account lockout using @Modifying JPQL queries to eliminate the read-increment-save race condition. The dev-mode authentication requires an explicit app.security.dev-mode=true flag — if Okta config is missing in production, the system fails hard rather than silently downgrading to a local JWT."

### 3.6 Anti-DoS & Performance

| Measure | Location | Impact |
|---------|----------|--------|
| FHIR pagination | _count (max=500) + _offset | 50K patients→max 500 per request |
| CSV streaming export | StreamingResponseBody, 500/page | 10K+ patients→streamed, no in-memory String |
| Emergency history limit | PageRequest.of(0, 500) | Prevents unbounded growth |
| 3-tier rate limiting | RateLimiterConfig (Redisson) | login 10/min, refresh 20/min, export 5/hour |
| RestTemplate pooling | SimpleClientHttpRequestFactory | 5s connect, 10s read timeout |
| Audit thread pool | auditExecutor (core=2, max=4, queue=500) | CallerRunsPolicy — never drop audit events |
| HikariCP | Default (max=10) | REQUIRES_NEW needs extra connections for audit |

---

## 4. Key Technical Decisions & Trade-offs

| Decision | Choice | Why | Trade-off |
|----------|--------|-----|-----------|
| AES mode | GCM vs CBC | GCM = AEAD (confidentiality+integrity) | IV must never repeat (SecureRandom 12B) |
| Key derivation | PBKDF2 310K vs SHA-256 | NIST SP 800-132 | ~50ms slower startup |
| Encryption layer | JPA @Convert vs Service layer | Business code completely unaware | Static bridge adds complexity |
| Audit isolation | REQUIRES_NEW vs REQUIRED | Audit failure never rolls back business | Extra DB connection per audit write |
| Audit deletion | archived soft-delete vs DELETE | 21 CFR Part 11 tamper-evident | Table growth needs periodic archiving |
| CDS blocking | WARNING vs BLOCK | Clinically appropriate | Requires override audit mechanism |
| Patient auth | Separate table vs same table | HIPAA audit separation | One extra JOIN |
| Frontend framework | React vs Vue | US healthcare market React >70% | Vue more popular in China |
| JWT storage | localStorage vs HttpOnly cookie | SPA simplicity | XSS risk (mitigated by React escaping) |
| API docs | Springdoc vs Knife4j | Spring Boot 3.4 compatibility | Knife4j 4.5 incompatible with Spring 6.2 |
| Dev auth | Explicit flag vs auto-detection | Prevent production downgrade | One extra config line for dev/CI |

---

## 5. Database Design

**Simplified ER:**
```
sys_user ──< sys_user_role >── sys_role ──< sys_role_menu >── sys_menu
   │
patient ──< patient_auth (1:1, auth separation)
   │
   ├──< appointment (patient_id, doctor_id→sys_user)
   ├──< prescription (patient_id, doctor_id→sys_user)
   │      └──< prescription_item
   ├──< bill (patient_id)
   ├──< message (sender_id, receiver_id)
   ├──< observation (patient_id)
   └──< consent (patient_id)

Standalone: audit_log, password_history, emergency_access, key_audit
CDS Rules: drug_interaction, drug_allergy_class, cds_override
Lab: loinc_catalog
Pharmacy: pharmacy_directory
Quality: quality_measure
```

---

## 6. Security Architecture — 6 Layers

```
Layer 1: Transport
  ├── TLS (MySQL SSL: useSSL=true&requireSSL=true)
  ├── HSTS (max-age=1yr, includeSubDomains)
  └── CORS (whitelisted origins)

Layer 2: Authentication
  ├── Okta OAuth2 Resource Server (production)
  ├── DevJwtEncoder (dev/h2 local)
  └── Explicit dev-mode flag (no auto-downgrade)

Layer 3: Authorization
  ├── RBAC: ADMIN / DOCTOR / PATIENT
  ├── @PreAuthorize method-level
  ├── Frontend JWT role parsing → sidebar filtering
  └── AdminGuard route protection

Layer 4: Data Encryption
  ├── AES-256-GCM at rest (JPA @Convert)
  ├── PBKDF2 key derivation
  ├── Versioned ciphertext for key rotation
  └── Redis @PhiField cache redaction

Layer 5: Audit
  ├── AOP @Auditable (business operations)
  ├── SHA-256 row_hash (tamper detection)
  ├── Login success/failure full trace
  └── Emergency access synchronous audit

Layer 6: Anti-Attack
  ├── 3-tier rate limiting (login/refresh/export)
  ├── FHIR pagination (max=500)
  ├── CSV streaming export
  └── RestTemplate pooling with timeout
```

---

## 7. API Endpoint Statistics

| Category | Count | Examples |
|----------|-------|----------|
| Auth | 6 | login, refresh, logout (staff + patient) |
| User Management | 8 | CRUD + profile + password change |
| Roles/Menus | 6 | CRUD for both |
| Patient | 6 | CRUD + FHIR case |
| Patient Portal | 8 | profile, appointments, prescriptions, bills, export, consent, password, self-edit |
| Appointments | 5 | CRUD + status filter |
| Prescriptions | 5 | CRUD + eRx transmit |
| Billing | 7 | CRUD + submit/adjudicate/pay/deny lifecycle |
| Chat | 4 | Staff + Patient messaging |
| Dashboard | 1 | Aggregate stats |
| Export | 2 | Streaming CSV |
| Audit | 1 | Multi-filter query API |
| FHIR | 6 | metadata + Patient + Observation + case |
| CDS | 1 | Drug interaction check |
| Integration | 2 | ADT + Lab Results |
| LOINC | 3 | Catalog + Panel + Trend |
| Pharmacy | 1 | Directory search |
| eCQM | 2 | Measures list + report |
| Consent | 4 | Admin CRUD + patient self-service |
| Emergency | 2 | Access + History |
| Key Audit | 1 | History query |
| **Total** | **~80** | |

---

## 8. Tech Stack

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Language | Java | 17 (LTS) | Healthcare industry standard |
| Framework | Spring Boot | 3.4.1 | Most comprehensive ecosystem |
| Security | Spring Security + OAuth2 | 6.x | Native OAuth2 Resource Server support |
| ORM | Spring Data JPA + Querydsl | Hibernate 6.x | JPA standard + type-safe dynamic queries |
| DB | MySQL | 8.0+ | Widely deployed RDBMS |
| Cache | Redis + Redisson | 7.x / 3.x | Distributed locks + rate limiting |
| FHIR | HAPI FHIR R4 | 7.4 | Only mature FHIR library for Java |
| API Doc | Springdoc OpenAPI | 2.6.0 | Spring Boot 3.4 compatible |
| Validation | Jakarta Validation | — | Declarative validation (@ValidPassword) |
| JSON | Jackson | — | Spring Boot default |
| Util | Lombok, Hutool | — | Reduce boilerplate |
| Build | Maven | 4.x | Dependency management |
| Testing | JUnit 5 + Spring Boot Test | — | 121 tests (112 integration + 9 unit) |

---

## 9. Common Interview Questions

### Encryption

**Q: Why AES-GCM instead of AES-CBC?**
GCM is AEAD — a single operation provides both confidentiality and integrity. CBC only provides confidentiality; you'd need a separate HMAC for integrity. In healthcare, tampered ciphertext is dangerous — modifying encrypted lab results (e.g., glucose value) could lead to misdiagnosis. With CBC the decryption would produce garbage silently; with GCM the authentication tag validation fails explicitly. The 128-bit tag is verified automatically by `Cipher.doFinal()`.

**Q: How does key rotation work?**
Ciphertext is prefixed with a 1-byte version number (0x01 = current key). Encryption writes `[0x01][IV][ciphertext+tag]`. Decryption reads the first byte: if 0x01 use current key, otherwise try previous key with fallback to current (backward compatibility). Rotation steps: set new key as `app.aes.key` → move old key to `app.aes.key.previous` → business writes gradually re-encrypt old data → remove previous key once clean. The `key_audit` table auto-logs every KEY_INIT/KEY_ROTATION event.

**Q: Why PBKDF2 instead of plain SHA-256 for key derivation?**
SHA-256 is a fast hash — an attacker can test ~10B passwords/second on GPU. PBKDF2 with 310,000 iterations + salt increases single-guess time from nanoseconds to milliseconds, raising brute-force cost by 6 orders of magnitude. NIST SP 800-132 recommends PBKDF2/PKCS#5 for password-to-key derivation.

**Q: Why return "[DECRYPT_FAILED]" as a string instead of throwing an exception?**
This is an intentional degradation strategy. During key rotation, a small number of old-format records is normal. If `decrypt()` threw an exception, the entire JPA query would fail (e.g., patient list completely broken) — far worse impact than a single field showing a placeholder. The frontend detects this string and displays "Data Unavailable (Compliance Protection)."

### Audit

**Q: How do you prevent an admin from tampering with audit logs?**
Multiple layers: (1) SHA-256 `row_hash` = hash(all data columns concatenated) per row; (2) `@SQLRestriction("archived=0")` plus `DataRetentionJob` only soft-deletes (SET archived=1); (3) Production recommendation: MySQL TRIGGER to block UPDATE/DELETE on audit_log. If an admin modifies a row, the hash won't match and an audit script can detect it. If they soft-delete, the record still exists in the table, just filtered by @SQLRestriction.

**Q: Why audit login failures?**
21 CFR Part 11 §11.300(b) requires an audit trail for every access event. Failed logins are security events — a burst of failures indicates a potential brute-force attack. Recording five distinct failure reason codes (USER_NOT_FOUND, ACCOUNT_DISABLED, ACCOUNT_LOCKED, BAD_CREDENTIALS, OKTA_AUTH_FAILED) enables forensic correlation.

**Q: What happens if the audit write fails?**
`AuditLogWriter.writeAsync()` runs on a dedicated thread pool with `REQUIRES_NEW` transactions. If the write fails (DB connection pool exhausted, timeout), only `log.error` is called — the already-committed business transaction is never rolled back. The thread pool uses `CallerRunsPolicy`: when the 500-capacity queue is full, the calling thread executes synchronously, guaranteeing no silent audit loss.

### FHIR

**Q: Why can't US Core race/ethnicity extensions use StringType?**
The US Core Implementation Guide requires `ombCategory` Codings with the OMB system URI (`urn:oid:2.16.840.1.113883.6.238`), not free-form text. CMS requires OMB 5-category reporting (White/Black/Asian/AI.AN/NH.PI) for eCQM quality measures. Non-compliant extensions cause validation failures and rejected CMS reports.

**Q: You advertise SMART on FHIR but the token endpoint is /api/v1/auth/login — is that correct?**
It's a simplified implementation. Full SMART on FHIR requires dedicated `/authorize` and `/token` endpoints with proper OAuth2 scopes. The current CapabilityStatement's `security.service` declares `SMART-on-FHIR`, and the `oauth-uris` extension points to `/api/v1/auth/login`, but the backend hasn't implemented standalone SMART endpoints yet. This is a documented future improvement.

### Clinical

**Q: How does CDS avoid blocking necessary prescriptions?**
CDS returns WARNING only — it never blocks the prescription. Doctors can override with documented reasoning stored in the `cds_override` table. Certain drug combinations are clinically necessary (e.g., Warfarin + Aspirin for specific cardiac patients); the final decision must always rest with the provider.

**Q: Why separate patient authentication (PatientAuth vs Patient)?**
HIPAA requires audit separation between authentication records and medical records. The `patient` table stores clinical data (high query volume), while `patient_auth` stores credentials and lockout state (queried only at login). Separating them prevents authentication queries from locking clinical data, and audits can distinguish "who logged in" (PatientAuth login audit) from "who accessed data" (PatientService operation audit).

### System Design

**Q: How did you fix the account lockout race condition?**
The original code was `read → increment → save` — three non-atomic steps. 10 concurrent failures could result in a counter of 2 instead of 11. The fix: `@Modifying @Query("UPDATE SysUser SET failedAttempts = COALESCE(failedAttempts,0)+1 WHERE id=:id")` — a single atomic database operation. The row lock ensures correctness under concurrency.

**Q: What happens if Okta goes down?**
Okta is a modern SaaS with 99.99%+ SLA. If it truly goes down: (1) Both staff and patients cannot log in — this is a systemic risk but extremely low probability; (2) Existing JWT tokens remain valid until expiry (default 2 hours); (3) Emergency Access endpoints still work if the user already holds a valid token. Never implement an Okta fallback at the application level — that would introduce greater security risk than the availability problem it solves.

**Q: How many concurrent users can this handle?**
The current monolithic architecture is designed for small-to-medium clinics (1-50 concurrent providers). For scale: (1) The audit thread pool is configurable (core/max pool size); (2) Redis caching reduces DB pressure; (3) FHIR endpoints are paginated; (4) CSV export is streamed. For hospital-scale (500+ concurrent), recommendations: split read/write services, introduce message queues for audit writes, use read replicas for queries.

---

## 10. Behavioral Interview Tips

**Structure for "Tell me about yourself":**
1. "I'm a backend engineer with [N] years of experience, recently focused on healthcare compliance."
2. "I built this HIPAA-compliant medical system covering encryption, audit, FHIR interoperability, and CDS."
3. "The project has 19 modules, 121 tests, and implements 21 CFR Part 11."
4. "I'm looking for a backend role where I can apply healthcare domain knowledge."

**Pick your "hardest problem" story:**
- Technical depth: AES key rotation with versioned ciphertext design
- Compliance: 21 CFR Part 11 audit tamper-proofing (hash chain + soft-delete)
- Architectural: Patient auth separation decision (PatientAuth vs Patient)
- Security fix: Explicit dev-mode flag to prevent production JWT downgrade

**"What would you do differently?":**
- "I'd start with compliance requirements upfront (21 CFR Part 11, HIPAA) rather than retrofitting."
- "I'd use Flyway/Liquibase for database migrations instead of raw schema.sql."
- "I'd add OpenTelemetry tracing for audit event correlation across services."
- "I'd implement the SMART on FHIR standalone OAuth2 endpoints for full SMART compliance."
