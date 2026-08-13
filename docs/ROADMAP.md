# Project Evolution Roadmap

> From the HIPAA + FHIR + US-Model foundation, through CDS, ePrescribing, compliance audit, frontend migration, multi-agent workflow, clinical data immutability, and full patient portal.
>
> **Status: Round 36 complete (21/22 gaps). Round 37: 49 findings → 49 resolved (8 CRITICAL, 12 HIGH, 18 MEDIUM, 11 LOW). 1 CRITICAL backend-dependent deferred (C4). L1 (any types) resolved in Round 43 — all Round 37 findings closed.**
>
> **Round 42 (2026-08-04): Integration tests decoupled from MySQL — run on isolated in-memory H2. `mvn clean install`: 135 tests, 0 failures (previously required a running MySQL and accumulated 8 broken tests as endpoints evolved).**
>
> **Full-system review (2026-08-04): Ready to merge — 0 CRITICAL, 0 HIGH. 2 MEDIUM (raw-entity responses in 6 endpoints; missing @Valid in 5 controllers), 2 LOW (silent catch in LoincCatalog + RxNorm lookup). See Post-Round 42 section.**
>
> **Full-system review II (2026-08-12): all findings fixed — R2-1 (CRITICAL) Round 45, R2-2 (HIGH) Round 47, R2-3..R2-9 Round 48. See Post-Round 44 section.**

---

## Prior Rounds (Archived)

| Round | Content | Status |
|-------|---------|--------|
| Pre-work | AES at-rest encryption, FHIR SSN masking, CSV masking, Redis PHI, audit patientId, key rotation | ✅ |
| 1 | Audit log query API, security response headers, account lockout | ✅ |
| 2 | Password policy, patient Right of Access, externalized token config | ✅ |
| 3 | FHIR resource endpoints, SMART scopes, US Core corrections | ✅ |
| 4 | Data retention, consent management, emergency access, key audit | ✅ |

---

## Round 5: CDS — Clinical Decision Support ✅ Complete

### Goal
When a provider prescribes medication, the system automatically checks for drug-drug interactions and allergy contraindications, preventing dangerous prescriptions.

### Feature Scope

| # | Feature | Description |
|---|---------|-------------|
| 5.1 | **Drug-Drug Interaction Rule Engine** | `drug_interaction` rules table storing drug pairs with severity levels (contraindicated / severe / moderate / minor). `PrescriptionService.create()` iterates through `items` pairwise before saving |
| 5.2 | **Drug-Allergy Contraindication Check** | Reads `Patient.allergies`, cross-references all prescribed drugs against allergy classes (e.g., "Penicillin" → penicillin family). Requires a `drug_allergy_class` table mapping drugs to allergy classes |
| 5.3 | **CDS Hook Endpoint** | FHIR CDS Hooks-compliant `POST /api/v1/fhir/cds-services` Discovery endpoint + `POST /api/v1/cds/drug-interaction-check` service endpoint, enabling external EHRs to call in |
| 5.4 | **Prescription Warning Response** | Interaction check results do not hard-block; returns `Warning` (severity + message + alternative drugs). Frontend displays warnings; provider may override + enter rationale + audit record |

### Data Model

```sql
-- Drug Interaction Rules Table
CREATE TABLE drug_interaction (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_a_code VARCHAR(20) NOT NULL,       -- RxNorm code of drug A
    drug_b_code VARCHAR(20) NOT NULL,       -- RxNorm code of drug B
    severity VARCHAR(20) NOT NULL,          -- contraindicated / severe / moderate / minor
    description VARCHAR(500) NOT NULL,      -- e.g. "Increased risk of QT prolongation"
    mechanism VARCHAR(200),                 -- pharmacologic mechanism
    recommendation VARCHAR(500)            -- clinical recommendation
);

-- Drug Allergy Class Mapping
CREATE TABLE drug_allergy_class (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    drug_rxnorm_code VARCHAR(20) NOT NULL,
    allergy_class VARCHAR(100) NOT NULL,    -- "Penicillin", "Sulfa", "NSAIDs"
    cross_reactive_codes VARCHAR(500)       -- comma-separated related RxNorm codes
);

-- Prescription Override Record (override audit)
CREATE TABLE cds_override (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    prescription_id BIGINT NOT NULL,
    warning_type VARCHAR(30) NOT NULL,      -- DRUG_DRUG / DRUG_ALLERGY
    severity VARCHAR(20),
    drugs_involved VARCHAR(200),
    override_reason VARCHAR(500) NOT NULL,
    overridden_by BIGINT NOT NULL,
    overridden_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Files Involved

| File | Action |
|------|--------|
| `common/config/CdsConfig.java` | CDS Hooks config |
| `module/prescription/service/CdsService.java` | New — core interaction check logic |
| `module/prescription/controller/CdsController.java` | New — CDS Hooks endpoint |
| `module/prescription/entity/DrugInteraction.java` | New |
| `module/prescription/entity/DrugAllergyClass.java` | New |
| `module/prescription/entity/CdsOverride.java` | New |
| `module/prescription/repository/*` | 3 corresponding Repositories |
| `module/prescription/service/PrescriptionService.java` | Modified — create() integrates CDS check |
| `resources/sql/schema.sql` | Add 3 new tables |

### Seed Data
- 20 common Drug-Drug Interactions (e.g., Warfarin+Aspirin=severe, Metformin+Contrast=contraindicated)
- 15 Drug-Allergy Class mappings

---

## Round 6: Integration Engine — ADT + Lab Results JSON API ✅ Complete

### Background

In modern healthcare architecture, HL7 v2 pipe messages are parsed by integration engines (Mirth Connect / Rhapsody / Corepoint) at the hospital side, converted to structured JSON, and sent via HTTP to the business backend. The backend does not need to embed an HL7 v2 parser.

```
Hospital EHR → HL7 v2 (MLLP) → Mirth Connect → JSON/HTTP → Our Backend
```

### Goal
Define the Mirth Connect post-transform JSON contract, receive ADT (admission/discharge/transfer) events and lab results, and persist the data.

### Feature Scope

| # | Feature | Description |
|---|---------|-------------|
| 6.1 | **ADT Event JSON Contract** | Define `AdtEvent` JSON schema: `eventType` (A01 admit/A03 discharge/A08 update), `patientMrn`, `patientName`, `dob`, `sex`, `admitDate`, `dischargeDate`, `department`. Document field mapping: JSON path → Patient/Appointment entity field |
| 6.2 | **ADT Event Processing** | `POST /api/v1/integration/adt` — receives ADT JSON, auto-upserts Patient (by MRN) + creates Admission Encounter (A01) or closes current Encounter (A03) |
| 6.3 | **Lab Results JSON Contract** | Define `LabResult` JSON schema: `patientMrn`, `orderCode`, `collectionDate`, `results[]` (`loincCode`, `value`, `unit`, `referenceRange`, `abnormalFlag`) |
| 6.4 | **Lab Results Processing** | `POST /api/v1/integration/lab-results` — receives lab result JSON → batch writes to `observation` table |
| 6.5 | **FHIR Observation Endpoint** | `GET /api/v1/fhir/Observation/{id}` + `GET /api/v1/fhir/Observation?patient={id}` — converts `observation` table data to FHIR Observation resources |
| 6.6 | **Idempotency Guarantee** | Integration engine may resend messages; dedup via `source_message_id` (Mirth message ID) |

### JSON Contract Examples

**ADT A01 (Admit):**
```json
{
  "sourceMessageId": "mirth-msg-12345",
  "eventType": "A01",
  "eventTime": "2026-06-01T08:30:00Z",
  "patient": {
    "mrn": "MRN-10001",
    "name": "James Anderson",
    "dateOfBirth": "1998-02-14",
    "sexAtBirth": "M",
    "address": { "line1": "1400 S Lake Shore Dr", "city": "Chicago",
                 "state": "IL", "zip": "60605" }
  },
  "visit": {
    "visitNumber": "VIS-78901",
    "admitDate": "2026-06-01T08:30:00Z",
    "department": "Cardiology",
    "admittingDoctorNpi": "1234567890"
  }
}
```

**Lab Result:**
```json
{
  "sourceMessageId": "mirth-msg-67890",
  "patientMrn": "MRN-10001",
  "orderCode": "CBC",
  "collectionDate": "2026-06-01T07:00:00Z",
  "results": [
    { "loincCode": "6690-2", "display": "WBC", "value": "7.2",
      "unit": "10*3/uL", "referenceRange": "4.0-11.0", "abnormalFlag": "N" },
    { "loincCode": "789-8", "display": "RBC", "value": "4.8",
      "unit": "10*6/uL", "referenceRange": "4.5-5.9", "abnormalFlag": "N" },
    { "loincCode": "718-7", "display": "HGB", "value": "14.1",
      "unit": "g/dL", "referenceRange": "13.5-17.5", "abnormalFlag": "N" }
  ]
}
```

### Data Model

```sql
CREATE TABLE observation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    patient_id BIGINT NOT NULL,
    loinc_code VARCHAR(20) NOT NULL,
    loinc_display VARCHAR(200),
    value VARCHAR(50),
    unit VARCHAR(20),
    reference_range VARCHAR(50),
    abnormal_flag CHAR(1),                -- N/L/H/LL/HH
    status VARCHAR(20) DEFAULT 'final',
    source_message_id VARCHAR(100),       -- Mirth message ID for dedup
    effective_date TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_obs_patient_loinc (patient_id, loinc_code),
    UNIQUE KEY uk_source_message (source_message_id)
);
```

### Files Involved

| File | Action |
|------|--------|
| `module/integration/controller/IntegrationController.java` | New — `POST /api/v1/integration/adt` + `/lab-results` |
| `module/integration/service/AdtService.java` | New — patient upsert + encounter management |
| `module/integration/service/LabResultService.java` | New — observation batch write + dedup |
| `module/integration/dto/AdtEventDTO.java` | New |
| `module/integration/dto/LabResultDTO.java` | New |
| `module/patient/entity/Observation.java` | New |
| `module/patient/repository/ObservationRepository.java` | New |
| `module/patient/controller/FhirObservationController.java` | New |
| `resources/sql/schema.sql` | Add `observation` table |

### No New Dependencies
Pure JSON over HTTP, no HL7 parsing library needed.

---

## Round 7: LOINC Lab Coding + Abnormal Flagging + Trend Analysis ✅ Complete

### Goal
Build a LOINC coding knowledge base on top of Round 6's `observation` table, supporting automated reference range matching, abnormal flagging, and trend queries.

### Feature Scope

| # | Feature | Description |
|---|---------|-------------|
| 7.1 | **LOINC Code Dictionary Table** | `loinc_catalog` table stores LOINC code/display/unit/reference range for common lab tests |
| 7.2 | **Automated Abnormal Flagging** | Auto-set `abnormal_flag` (N/L/H/LL/HH/AA) based on reference range, supporting age/gender-stratified reference ranges |
| 7.3 | **Lab Trend Query** | `GET /api/v1/patients/{id}/observations?loinc=` returns historical trend for a patient's lab test (ordered by time) |
| 7.4 | **Panel Support** | CBC = WBC+RBC+HGB+HCT+PLT; BMP = Glucose+Ca+Na+K+CO2+Cl+BUN+Creatinine. Supports panel expansion |

### Data Model

```sql
CREATE TABLE loinc_catalog (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loinc_code VARCHAR(20) UNIQUE NOT NULL,
    display VARCHAR(200) NOT NULL,
    unit VARCHAR(20),
    ref_range_low VARCHAR(20),
    ref_range_high VARCHAR(20),
    panel_parent_code VARCHAR(20)          -- e.g. CBC's LOINC code for panel grouping
);
```

### Files Involved

| File | Action |
|------|--------|
| `module/patient/entity/LoincCatalog.java` | New |
| `module/patient/repository/LoincCatalogRepository.java` | New |
| `module/patient/service/LabResultService.java` | New — trend + abnormal flagging |
| `module/patient/controller/LabResultController.java` | New |
| `resources/sql/schema.sql` | Add `loinc_catalog` table |

### Seed Data
29 common LOINC codes (CBC 8 + BMP 8 + Lipid 4 + HbA1c + TSH + UA 8).

---

## Round 8: ePrescribing + EPCS ✅ Complete

### Goal
Support electronic prescription transmission to pharmacies and EPCS (Electronic Prescribing of Controlled Substances) compliance.

### Feature Scope

| # | Feature | Description |
|---|---------|-------------|
| 8.1 | **Pharmacy Directory** | `pharmacy_directory` table storing pharmacy NPI, name, address, supported e-prescribing standards (NCPDP SCRIPT) |
| 8.2 | **NCPDP SCRIPT Message Generation** | Generate NCPDP SCRIPT 10.6-compliant NewRx message (XML), optionally encrypted |
| 8.3 | **EPCS Controlled Substance Workflow** | Controlled substances (Schedule II-V) require two-factor authentication + separate auditing. Triggered when `Prescription.controlledSchedule` is non-null |
| 8.4 | **Prescription Status Tracking** | `rx_status` enhanced: active → transmitted → received → dispensed → picked_up, tracking the full e-prescribing lifecycle |
| 8.5 | **Pharmacy Selection Interface** | `GET /api/v1/pharmacies?zip=&distance=` retrieve nearby pharmacies |

### Data Model

```sql
CREATE TABLE pharmacy_directory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    npi VARCHAR(10) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL,
    address_line1 VARCHAR(100),
    city VARCHAR(50),
    state CHAR(2),
    zip_code VARCHAR(10),
    phone VARCHAR(200),
    supports_epcs TINYINT DEFAULT 0
);
```

### Files Involved

| File | Action |
|------|--------|
| `module/prescription/entity/PharmacyDirectory.java` | New |
| `module/prescription/repository/PharmacyDirectoryRepository.java` | New |
| `module/prescription/controller/PharmacyController.java` | New |
| `module/prescription/service/EpcsService.java` | New — EPCS two-factor + audit |
| `module/prescription/service/NcpdpScriptService.java` | New — NewRx XML generation |
| `module/prescription/service/PrescriptionService.java` | Modified — add transmit/send methods |

---

## Round 9: eCQM — Clinical Quality Measures ✅ Complete

### Goal
Calculate CMS MIPS/MACRA clinical quality measures.

### Feature Scope

| # | Feature | Description |
|---|---------|-------------|
| 9.1 | **Measure Definition Engine** | `quality_measure` table defines measures (population, denominator, numerator, exclusions), based on FHIR eCQM Measure resource |
| 9.2 | **HbA1c Control (CMS122v11)** | Diabetic patients with HbA1c < 9%, requiring 1+ lab records |
| 9.3 | **Breast Cancer Screening (CMS125v11)** | Women aged 50-74 with mammogram within 27 months |
| 9.4 | **Hypertension Control (CMS165v11)** | Hypertensive patients with most recent BP < 140/90 |
| 9.5 | **Measure Report Export** | `GET /api/v1/quality/measures/{cmsId}/report?period=2026` returns CMS-format measure report JSON |

### Data Model

```sql
CREATE TABLE quality_measure (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cms_id VARCHAR(20) UNIQUE NOT NULL,     -- e.g. "CMS122v11"
    title VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    denominator_query VARCHAR(1000),        -- SQL or JPQL criteria
    numerator_query VARCHAR(1000),
    exclusion_query VARCHAR(1000),
    report_period_months INT DEFAULT 12
);
```

### Files Involved

| File | Action |
|------|--------|
| `module/quality/entity/QualityMeasure.java` | New |
| `module/quality/entity/QualityResult.java` | New |
| `module/quality/repository/*` | 2 Repositories |
| `module/quality/service/QualityMeasureService.java` | New — SQL query + calculation |
| `module/quality/controller/QualityController.java` | New |
| `resources/sql/schema.sql` | Add 2 tables |

### Seed Data
3 CMS eCQM definitions (CMS122v11, CMS125v11, CMS165v11).

---

## Execution Priority (All Complete)

```
Round 5  CDS (Drug-Drug + Drug-Allergy)              ✅ 2026-06-01
Round 6  Integration (ADT + Lab Results JSON)        ✅ 2026-06-01
Round 7  LOINC Coding + Abnormal Flag + Trend        ✅ 2026-06-01
Round 8  ePrescribing + EPCS                         ✅ 2026-06-01
Round 9  eCQM Clinical Quality Measures              ✅ 2026-06-01
```

**No new Maven dependencies needed.** All rounds are pure business logic on the existing Spring Boot/HAPI FHIR stack.

## Actual File Statistics

| Round | New Files | Modified Files | Commit |
|-------|----------|---------------|--------|
| 5 CDS | 9 | 3 | `4390489` |
| 6 Integration | 8 | 2 | `290476d` |
| 7 LOINC | 4 | 2 | `6fbeb07` |
| 8 ePrescribing | 5 | 3 | `5a08006` |
| 9 eCQM | 4 | 2 | `13800ec` |
| **Total** | **30** | **12** | — |

---

# Round 10: Remaining PHI Field At-Rest Encryption ✅ Complete

> **Status: Complete (2026-06-02)**

Added encryption for 12 additional Patient fields: address, city/state/zip, emergencyContactName, insurancePayer/GroupNumber, primaryCareProvider, medicalHistory (TEXT→VARCHAR 4000), allergies (VARCHAR→2000), dateOfBirth (LocalDate→VARCHAR 100).

`LocalDateAttributeConverter` created. Patient table: 19/29 fields AES-256-GCM encrypted + 1 field LocalDate encrypted.

---

# Round 11–13: HIPAA/21 CFR Part 11 Compliance Security Audit ✅ All Complete

> **Source:** Hell-level compliance audit (2026-06-02)
> **Status: 3/3 Rounds Complete (2026-06-03)**

---

## Round 11: CRITICAL Red-Line Fixes ✅ Complete

### 11.1 Login Audit Trail (21 CFR Part 11 §11.300)

| Task | File | Description |
|------|------|-------------|
| Login success audit | `AuthService.login()` | Added `@Auditable(module="auth", action="LOGIN_SUCCESS")` |
| Login failure audit | `AuthService.login()` | New `@AfterThrowing` aspect captures failure events |
| Patient login audit | `PatientAuthController.login()` | Same as above |
| Token refresh audit | `AuthService.refresh()`, `PatientAuthController.refresh()` | Added `@Auditable` |
| Logout audit | `AuthController.logout()` | Added `@Auditable` |

### 11.2 Production MySQL SSL Fix

| Task | File | Description |
|------|------|-------------|
| Complete SSL params | `application-prod.yml:3` | JDBC URL added `useSSL=true&requireSSL=true&verifyServerCertificate=true` |

### 11.3 Audit Log Tamper Protection

| Task | File | Description |
|------|------|-------------|
| Audit table append-only | `schema.sql` | Added DB TRIGGER to block UPDATE/DELETE |
| Hash chain integrity | `AuditLog.java` | Added `SHA-256(prev_hash \|\| this_row)` column |
| Remove physical delete | `DataRetentionJob.java` | `deleteByCreateTimeBefore()` → soft-delete + archive |
| Immutable entity | `AuditLog.java` | Extends `BaseEntity`, added `@SQLRestriction` |

### 11.4 PatientVO @PhiField Completion

| Task | File | Description |
|------|------|-------------|
| 15 fields annotated | `PatientVO.java` | `addressLine1/2`, `city`, `state`, `zipCode`, `dateOfBirth`, `medicalHistory`, `allergies`, `emergencyContactName/Phone/Relation`, `insurancePayer/MemberId/GroupNumber`, `primaryCareProvider` added `@PhiField` |

### 11.5 Role/Menu Permission Change Auditing

| Task | File | Description |
|------|------|-------------|
| Role CRUD audit | `SysRoleService.java` | create/update/delete added `@Auditable` |
| Menu CRUD audit | `SysMenuService.java` | create/update/delete added `@Auditable` |

---

## Round 12: HIGH Priority Fixes ✅ Complete

### 12.1 Electronic Signature (21 CFR Part 11 §11.200)

| Task | File | Description |
|------|------|-------------|
| Prescription sign two-factor | `PrescriptionController.transmit()` | Require re-enter password + TOTP |
| Signature audit record | `EpcsService.java` | Implement TODO comment EPCS two-factor verification |
| Billing/consent signature | `BillController`, `ConsentController` | Critical operations require signature confirmation |

### 12.2 Audit Log Archival (Not Deletion)

| Task | File | Description |
|------|------|-------------|
| WORM archive service | New `AuditArchiveService.java` | 6-year-old records moved to append-only storage |
| Remove hard delete | `DataRetentionJob.java` | Changed to mark `archived=true` |

### 12.3 AES Key Derivation Upgrade (NIST SP 800-132)

| Task | File | Description |
|------|------|-------------|
| PBKDF2 replaces SHA-256 | `AesCryptoUtil.deriveKey()` | Use PBKDF2-HMAC-SHA256, 310,000 iterations + random salt |

### 12.4 Add Pagination to Unpaginated Queries

| Task | File | Description |
|------|------|-------------|
| FHIR Patient pagination | `FhirPatientController.java` | Added `_count`/`_offset` params, max=500 |
| FHIR Observation pagination | `FhirObservationController.java` | Same as above |
| Other findAll() endpoints | 6 controllers | Added pagination or upper limit |

### 12.5 CSV Streaming Export

| Task | File | Description |
|------|------|-------------|
| StreamingResponseBody | `ExportController.java` | Write row-by-row instead of in-memory String build |
| JPA Stream query | `PatientRepository` | Added `streamAll()` method |

### 12.6 PatientService.update() PHI Masking

| Task | File | Description |
|------|------|-------------|
| phiAccess=true | `PatientService.update()` | Prevents `PatientFormDTO` serialization in audit detail |

---

## Round 13: MEDIUM Optimization & Hardening ✅ Complete

### 13.1 Refresh Token Rate Limiting

| Task | File | Description |
|------|------|-------------|
| Refresh URI rate limit | `RateLimiterConfig.java` | Added `/refresh` match, 20/min/IP |

### 13.2 Okta RestTemplate Connection Pooling

| Task | File | Description |
|------|------|-------------|
| Shared RestTemplate Bean | `AuthService.java` | Connection pool + 5s timeout + circuit breaker (staff Okta calls only; patient auth has no external IdP dependency) |

### 13.3 Account Lockout Atomicity

| Task | File | Description |
|------|------|-------------|
| Atomic failure count | `SysUserRepository.java` | `@Modifying UPDATE SET failed_attempts = failed_attempts + 1` |

### 13.4 Emergency Access Reason Sanitization

| Task | File | Description |
|------|------|-------------|
| Predefined reason codes | `EmergencyAccessController.java` | Restrict `reason` to enum values or regex sanitization |

### 13.5 Remove Hardcoded Keys

| Task | File | Description |
|------|------|-------------|
| Enforce env vars | `application-*.yml` | Remove default keys/passwords from dev/h2 configs |
| Vault integration | New | Optional: integrate HashiCorp Vault |

---

## Execution Priority (All Complete)

```
Round 11  CRITICAL Red-Line    ✅ 2026-06-02  (5 items: login audit/prod SSL/tamper proof/@PhiField/role audit)
Round 12  HIGH Priority        ✅ 2026-06-03  (4 items: PBKDF2/pagination/streaming CSV/phiAccess)
Round 13  MEDIUM Hardening     ✅ 2026-06-03  (3 items: refresh rate limit/RestTemplate/atomic lock)
```

| Round | Commit | Fix Count |
|-------|--------|-----------|
| 11 | `8d4ed44` | 5 |
| 12 | `d9970d8` | 4 |
| 13 | `3f68311` | 3 |

---

# Round 14–15: Frontend Migration ✅ All Complete

> **Status: 2/2 Rounds Complete (2026-06-03)**

## Round 14: Frontend TypeScript Migration + PatientForm Component ✅ Complete

| Task | Description | Commit |
|------|-------------|--------|
| Vue JS→TS | 20 Vue SFCs converted to `<script setup lang="ts">`; 17 JS files renamed .ts | `6beb1a6` |
| PatientForm | Full US medical model form: OMB race, structured address, insurance fields, FHIR Bundle parsing, PHI masking, [DECRYPT_FAILED] defensive handling | `6beb1a6` |
| Dashboard navigation | Stat card click navigates to corresponding module route | `6beb1a6` |

## Round 15: Full React Migration ✅ Complete

| Task | Description | Commit |
|------|-------------|--------|
| Vue removal | Deleted all `.vue` SFC files | `50207ae` |
| React + TS | React 18 + TypeScript + CSS Modules + Vite 5 | `50207ae` |
| 30+ components | StaffLayout, Login, Dashboard, Patients, Appointments, Prescriptions, Billing, Profile, System CRUD, full Patient portal suite | `50207ae` |

| Round | Commit | Description |
|-------|--------|-------------|
| 14 | `6beb1a6` | Vue TypeScript migration + PatientForm |
| 15 | `50207ae` | Vue→React full rewrite |

---

# Round 16: CDS — Real-Time Commercial DDI API (Deferred)

> **Status: Deferred — low priority, implement only if operational need arises**
>
> **Reason:** Current CDS knowledge base (`drug_interaction` table) relies on manual seed data. RxNav has shut down its Interaction API. The industry standard is now subscription-based commercial APIs (DrugBank, First Databank, MediSpan, etc.) that provide real-time DDI results — no local knowledge base syncing needed. The approach is fundamentally different: rather than pulling data into a local table and doing pairwise lookups, call the vendor API in real time at prescription creation.

## Background

Modern DDI checking has moved away from local rule tables:
- **RxNav Interaction API** — shut down by NLM, no longer available.
- **Commercial APIs** — DrugBank, First Databank (FDB), MediSpan, Cerner Multum — provide real-time interaction screening via REST/gRPC. The vendor maintains the knowledge base; the backend only sends the drug list and receives interaction results.
- **Industry shift** — local drug-drug comparison is increasingly rare outside legacy EHRs.

## Approach

Replace local `drug_interaction` table lookup with a pluggable external DDI provider:

```
PrescriptionService.create()
  └─ CdsService.checkDrugInteractions(items)
       ├─ match whitelist → skip (known-safe combination, e.g. Metformin+Metformin ER)
       ├─ cache hit (local drug_interaction, source=API) → return cached result
       ├─ cache miss + provider configured → call vendor API, save result to local table
       └─ provider unavailable + cache miss → fallback to local manual rules
```

### Three Roles of the Local Table

| Role | source column | Purpose |
|------|-------------|---------|
| **Whitelist** | `MANUAL` + `severity = 'safe'` | Known-safe combinations that should never raise a warning, even if a vendor API flags them. Clinician-curated. |
| **Cache** | `DRUGBANK` / `FDB` / etc. | API results persisted locally. On subsequent checks for the same drug pair, skip the API call entirely. Protects against API failures for commonly prescribed combinations. |
| **Fallback** | `MANUAL` (existing seed data) | Default behavior when no vendor API is configured. Preserved as zero-cost baseline. |

## Feature Scope

| # | Feature | Description |
|---|---------|-------------|
| 16.1 | **DDI Provider Interface** | `DdiProvider` interface — `List<CdsWarning> check(List<String> rxnormCodes)`. Pluggable implementations behind `@ConditionalOnProperty` |
| 16.2 | **DrugBank Adapter** | `DrugBankDdiProvider` — calls DrugBank Interaction API, maps response to `CdsWarning` list. API key configured via `app.cds.provider.api-key` |
| 16.3 | **Local Cache Layer** | API results upserted into `drug_interaction` with `source = provider name`. Next check hits local DB first — no API call needed for previously seen drug pairs |
| 16.4 | **Whitelist** | `drug_interaction` rows with `severity = 'safe'` act as explicit whitelist. Whitelisted pairs are skipped before any API call or local rule check. Clinicians manage whitelist via admin API |
| 16.5 | **Check Order** | Whitelist → local cache → vendor API → manual rules. Each step short-circuits on match |
| 16.6 | **Config Toggle** | `app.cds.provider.type: NONE` (default). `NONE` uses local table only; `DRUGBANK` / `FDB` enables cache + API mode |

## Plan

1. Add `source VARCHAR(20) DEFAULT 'MANUAL'` column to `drug_interaction`; add `safe` to severity enum
2. Define `DdiProvider` interface in `module/prescription/service/ddi/`
3. Refactor `CdsService.checkDrugInteractions()` to layered check: whitelist → cache → API → fallback
4. Implement `DrugBankDdiProvider` — call API, map response, upsert to local table with `source = 'DRUGBANK'`
5. Add admin endpoints: `GET/POST/DELETE /api/v1/cds/whitelist` for clinician-managed whitelist entries
6. Add `application.yml` config with all gating disabled by default

## Files Involved

| File | Action |
|------|--------|
| `module/prescription/service/ddi/DdiProvider.java` | New — interface |
| `module/prescription/service/ddi/DrugBankDdiProvider.java` | New — reference adapter |
| `module/prescription/service/CdsService.java` | Modified — layered check logic |
| `module/prescription/controller/CdsController.java` | Modified — whitelist CRUD endpoints |
| `module/prescription/entity/DrugInteraction.java` | Modified — add `source` column, `safe` severity |
| `resources/sql/schema.sql` | Modified — alter `drug_interaction` |
| `application.yml` | Modified — add `app.cds.provider.*` config |

## Risk & Trade-offs

- **Commercial API cost** — subscription required; this is the primary reason this feature is deferred.
- **Latency** — external API call adds ~200-500ms on cache miss only. Cache hits (common drugs) stay sub-millisecond. Circuit breaker prevents blocking if provider is down.
- **Whitelist responsibility** — clinician-curated; stale whitelist entries could suppress real interactions. Admin UI should show `created_at` and `created_by` for audit.
- **Cache invalidation** — API results cached indefinitely. Optionally add `cached_at` timestamp + configurable TTL for future refresh.
- **Fail-open** — if vendor API is unreachable and no cache hit, fall through to local manual rules. If no local rule either, prescription proceeds (log warning).

---

# Round 17: RBAC Security Remediation ✅ Complete

> **Status: All 11 items complete (2026-06-30)**
>
> **Source:** RBAC audit (2026-06-29). 12 findings remediated, 11 implemented, 1 informational.

---

## 17-1 CRITICAL: Externalize JWT signing key + separate issuer per profile

| Task | File | Description |
|------|------|-------------|
| Add prod JwtDecoder/JwtEncoder bean | `SecurityConfigProd.java` | New `@Profile("prod")` config reading key from `AES_KEY` env var or HashiCorp Vault |
| Remove hardcoded dev key | `SecurityConfigDev.java` | Read `app.security.dev-jwt-secret` from application-dev.yml, with fallback only for h2 |
| Add `iss` claim to JWT | `AuthService.java`, `PatientAuthController.java` | Set `issuer("medical-server")` for staff, `issuer("medical-server/patient")` for patient tokens |
| Validate `iss` in mapper | `JwtClaimMapper.java` | Reject tokens where staff endpoint receives patient-issued token or vice versa |
| Add `aud` claim | `AuthService.java`, `PatientAuthController.java` | Set audience for staff vs patient endpoints |

**Risk:** Without this fix, anyone with knowledge of the hardcoded string can forge arbitrary role tokens.

---

## 17-2 HIGH: Enforce emergency access expiry in data access layer

| Task | File | Description |
|------|------|-------------|
| Add emergency session token | `EmergencyAccessController.java` | Generate a short-lived (30min) JWT with `scope=EMERGENCY` + `patientId` claim instead of returning full patient data directly |
| Validate emergency scope | `PatientController.java`, `FhirPatientController.java` | Accept emergency token as alternative auth for patient-specific endpoints, reject if expired or wrong patient |
| Add `@PreAuthorize` guard | `EmergencyAccessController.java` | Require explicit EMERGENCY scope on follow-up data access |

**Risk:** Current implementation creates an audit log entry but provides no actual access control — the 30-minute window is never enforced.

---

## 17-3 HIGH: Scope patient export to own patients only

| Task | File | Description |
|------|------|-------------|
| Add doctor-patient relationship filter | `ExportController.java`, `PatientRepository.java` | DOCTOR role can only export patients they have appointments/prescriptions with; ADMIN retains full access |
| Add `@PreAuthorize` check | `ExportController.java` | Replace `hasAnyRole(ADMIN,DOCTOR)` with role-specific query scoping |

**Risk:** Any doctor can currently export the entire patient database as CSV including PHI fields.

---

## 17-4 HIGH: Token revocation for disabled accounts

| Task | File | Description |
|------|------|-------------|
| Add `forceLogoutAfter` timestamp to `SysUser` | `SysUser.java`, `schema.sql` | Set to `now()` when account is disabled, password changed, or role changed |
| Validate in `JwtClaimMapper` | `JwtClaimMapper.java` | Check `iat` claim against `forceLogoutAfter` — reject tokens issued before the revocation timestamp |
| Cache revocation timestamp | `AuthService.java` | Redis cache with 2-min TTL to avoid DB hit on every request |

**Risk:** Disabled accounts retain access for up to 2 hours (token expiry). No way to immediately revoke a compromised session.

---

## 17-5 MEDIUM: Emergency access audit review flow

| Task | File | Description |
|------|------|-------------|
| Default `audited=0` | `EmergencyAccessController.java` | Remove `ea.setAudited(1)` — let `@PrePersist` default to 0 |
| Add review endpoint | `EmergencyAccessController.java` | `PUT /api/v1/emergency/{id}/review` — ADMIN only, sets `audited=1` + `reviewedBy` + `reviewedAt` |
| Add pending review list | `EmergencyAccessController.java` | `GET /api/v1/emergency?audited=0` — ADMIN only, lists unreviewed emergency accesses |

---

## 17-6 MEDIUM: Re-authentication for sensitive profile changes

| Task | File | Description |
|------|------|-------------|
| Require password for NPI/DEA/license changes | `UserProfileController.java` | Add `currentPassword` field to update request; validate with `passwordEncoder.matches()` before applying changes |
| Add `@Auditable` | `UserProfileController.java` | Audit log when professional credentials are modified |

**Risk:** A staff member can change their NPI, DEA number, or license without re-entering their password — a stolen session allows credential hijacking.

---

## 17-7 MEDIUM: Add @PreAuthorize to unprotected controllers

| Task | File | Description |
|------|------|-------------|
| Add `@PreAuthorize` | `UserProfileController.java` | `hasAnyRole('ADMIN','DOCTOR')` — currently no guard |
| Add `@PreAuthorize` | `ChatSseController.java` | `hasAnyRole('ADMIN','DOCTOR','PATIENT')` — currently relies only on SecurityConfig chain |

---

## 17-8 MEDIUM: Remove plaintext credential logging

| Task | File | Description |
|------|------|-------------|
| Redact log message | `DataInitializer.java` | Change `log.info("Seed data initialized (admin/admin123, ...)")` to `log.info("Seed data initialized (admin: bcrypt, doctor1: bcrypt, patient1: bcrypt)")` |

---

## 17-9 MEDIUM: Return role changes in token refresh

| Task | File | Description |
|------|------|-------------|
| Fetch current roles on refresh | `AuthService.java` | In Okta refresh flow, re-extract roles from the new access token claims |
| Populate roles/permissions | `AuthService.java` | `LoginResponse.forRefresh()` should query current role/permission list, not pass `List.of()` |

**Risk:** After role change, clients operate with stale cached roles until next full login.

---

## 17-10 LOW: Wire permission-based authorization

| Task | File | Description |
|------|------|-------------|
| Convert `@PreAuthorize("hasRole('ADMIN')")` to `hasAuthority('system:user:list')` | Multiple controllers | Replace role checks with specific permission checks where granularity matters |
| Rename permission strings | `LoginUser`, `JwtClaimMapper` | Ensure permissions use consistent prefix, or create without prefix and use `hasAuthority()` |

**Note:** Low priority — current role-based model is functional. Permission-based model would enable finer-grained control (e.g., doctor who can view prescriptions but not create them).

---

## 17-11 LOW: Verify FHIR metadata endpoint exists

| Task | File | Description |
|------|------|-------------|
| Check and add if missing | `FhirPatientController.java` or new controller | `GET /api/v1/fhir/metadata` returning CapabilityStatement |
| Remove from permitAll if not implemented | `SecurityConfig.java` | Don't leave dead permitAll entries |

---

## Execution Order (All Complete ✅)

```
17-1  CRITICAL  JWT key externalization + iss/aud separation     ✅ 9baa70e
17-2  HIGH      Emergency access enforcement                     ✅ 8fe38cc
17-3  HIGH      Patient export scoping                           ✅ 4d14735
17-4  HIGH      Token revocation for disabled accounts           ✅ 2fafb6f
17-5  MEDIUM    Emergency audit review flow                      ✅ db46fb6
17-6  MEDIUM    Profile re-authentication                        ✅ 16a061a
17-7  MEDIUM    Missing @PreAuthorize                            ✅ 200cdea
17-8  MEDIUM    Plaintext credential logging                     ✅ bc58e67
17-9  MEDIUM    Token refresh role sync                          ✅ c0a43fb
17-10 LOW       Permission-based authorization                   ✅ 1fd5d60
17-11 LOW       FHIR metadata endpoint                           ✅ 07a0f73
```

---

# Round 18: "Message Patient" Button on Patient List ✅ Complete

> **Status: Complete (2026-07-06)**

## Goal
Add a "Message" button to the patient list page so doctors can initiate a chat directly without navigating to the Messages page first.

## Changes

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/views/patients/index.tsx` | Modify | Add "Msg" button navigating to `/chat?partnerId=&partnerName=` |
| `medical-web/src/views/chat/index.tsx` | Modify | Read `partnerId`/`partnerName` from URL query params, auto-select conversation on mount |

---

# Round 19: Frontend-Backend Alignment + Patient Payment ✅ Complete

> **Status: Complete (2026-07-06)**

## Goal
Comprehensive audit of all 29 backend controllers (~62 endpoints) against frontend API layer and UI views. Fix clinical workflow gaps: prescriptions (no create/edit), billing (list-only, no workflow), dashboard (hardcoded data), token refresh (never called), patient portal (missing password change + export + bill payment).

## Changes

### Prescriptions — Full CRUD + Transmit

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/views/prescriptions/index.tsx` | Modify | Add +Add Prescription button, Edit per row, modal form (patient dropdown, dynamic items list), Transmit button with pharmacy picker |
| `medical-web/src/api/prescription.ts` | Modify | Add `transmitPrescription(id, pharmacyId)` → `PUT /prescriptions/{id}/transmit` |
| `medical-web/src/api/pharmacy.ts` | **New** | `getPharmacies(params?)` → `GET /pharmacies` |

### Billing — Full Claim Lifecycle

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/views/billing/index.tsx` | Modify | Add +Create Bill form, Submit (DRAFT→SUBMITTED), Adjudicate modal (insurance/adjustment/claim#), Deny. Pay removed from staff page (patient function) |
| `medical-web/src/api/bill.ts` | Modify | Add `submitBill`, `adjudicateBill`, `payBill`, `denyBill` |
| `medical-server/.../PatientPortalController.java` | Modify | Add `PUT /patient/me/bills/{id}/pay` — patient self-payment with ownership check |

### Patient Portal — Password + Export + Pay Bills

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/views/patient/bills/index.tsx` | Modify | Add Pay Now button, payment modal (amount auto-filled, method picker) |
| `medical-web/src/views/patient/profile/index.tsx` | Modify | Add Change Password section |
| `medical-web/src/views/patient/layout/PatientLayout.tsx` | Modify | Add Export My Data sidebar link |

### Dashboard — Real Stats Endpoint

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/views/dashboard/index.tsx` | Modify | Use `GET /dashboard/stats` instead of 3 separate API calls. Prescriptions card fixed from hardcoded "-" |
| `medical-web/src/api/dashboard.ts` | **New** | `getDashboardStats()` → `GET /dashboard/stats` |

### Auth — Token Auto-Refresh

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/api/request.ts` | Modify | 401 interceptor: try `POST /auth/refresh` before redirecting to login. Concurrent requests queued during refresh |
| `medical-web/src/views/login/index.tsx` | Modify | Store `refreshToken` on login |
| `medical-web/src/layout/StaffLayout.tsx` | Modify | Clear `refreshToken` on logout |

---

# Round 20: Multi-Agent Workflow Infrastructure ✅ Complete

> **Status: Complete (2026-07-07)**

## Goal
Set up specialized agent configuration files to enable structured multi-agent development (Plan → Frontend + Backend → Review). Each agent has role-specific scope, patterns, constraints, and output formats.

## Agent Configs

| File | Role | Key Constraints |
|------|------|----------------|
| `.claude/agents/plan.md` | Architecture designer | Explores codebase, identifies files, designs execution order. Does NOT write code. References CLAUDE.md as authority |
| `.claude/agents/frontend.md` | React/TS implementer | API module import pattern, modal/form/table CSS conventions, falsy safety rules, patient vs staff view differences |
| `.claude/agents/backend.md` | Spring Boot implementer | Package convention, DTO/Entity/Service patterns, @SQLDelete/@SQLRestriction, @Auditable, PHI encryption (both @Convert and AesCryptoUtil) |
| `.claude/agents/review.md` | Adversarial reviewer | Per-file checklist (backend + frontend + cross-cutting), severity levels, common bug patterns, VERDICT line |

---

# Round 21: CDS Frontend Integration ✅ Complete

> **Status: Complete (2026-07-07)**
> **Method: First Workflow-driven multi-agent round (Plan → Implement → Review)**

## Goal
Integrate Clinical Decision Support (drug-drug interaction + drug-allergy contraindication) checks into the prescription create/edit flow. When a provider prescribes medication, the frontend calls `POST /api/v1/cds/check` before saving. Warnings are displayed in a modal with severity colors; the provider can override with a risk-acknowledgment checkbox or cancel to edit.

## Workflow Stats
- **3 agents**: Plan (architecture design), Implement (frontend code), Review (adversarial checklist)
- **44 tool calls**, **~84K tokens**, **115s**
- **Review found 1 CRITICAL** (rxnormCode always `''` → CDS no-op) — fixed before commit

## Changes

| File | Action | Description |
|------|--------|-------------|
| `medical-web/src/api/cds.ts` | **New** | `checkCds(data)` → `POST /api/v1/cds/check` |
| `medical-web/src/views/prescriptions/CdsWarningModal.tsx` | **New** | Modal: severity-colored badges, type labels, drugs involved, description, recommendation, "I understand" checkbox, Override & Save / Cancel |
| `medical-web/src/views/prescriptions/index.tsx` | Modify | CDS check before save (try/catch fallback), rxnormCode field in items form, RxNorm auto-lookup (functional setForm to avoid stale closure), `doSave()` helper, deferred `setShowForm(false)` |
| `medical-server/.../prescription/controller/CdsController.java` | Modify | Add `GET /api/v1/cds/drugs?rxnorm=` — lookup drug name by RxNorm code from prescription_item table |
| `medical-server/.../prescription/repository/PrescriptionItemRepository.java` | Modify | Add `findDrugNamesByRxnormCode()` query |

## CDS Flow
```
User clicks Save
  → POST /api/v1/cds/check {patientId, items[{rxnormCode, drugName}]}
  → passed=true → save normally
  → warnings → show CdsWarningModal
      → Override & Save → save despite warnings
      → Cancel → stay on form, edit prescription
  → CDS error → save anyway (fail-open)

RxNorm auto-lookup:
  User types RxNorm code (e.g. 6809)
  → GET /api/v1/cds/drugs?rxnorm=6809
  → returns {rxnormCode: "6809", drugName: "Metformin HCl"}
  → drug name field auto-filled
  → stale response guard: ignores result if code has changed since request
```

### Post-Release Fixes
- **Stale closure bug**: `handleRxnormChange` used captured `form` in async callback → 4th digit of RxNorm code disappeared. Fixed with functional `setForm(prev => ...)` + stale-response guard.
- **Agent configs updated**: all 4 agent files now include Round 21 lessons (data contract traceability, no-op detection, Vite `--force`, `|| null` bug pattern, stale closure detection).

### Verified
- RxNorm lookup: 6 codes all return correct drug names (tested)
- CDS drug-drug interaction: Metformin(6809) + Ibuprofen(5640) → moderate warning (tested)
- CDS drug-allergy: Amoxicillin(308191) + Penicillin allergy → contraindicated (tested)
- Full combo 3 drugs: 4 warnings across 2 types (tested)
- Frontend modules served correctly (checked via Vite proxy)

---

# Round 22: Patient Portal Token Auto-Refresh ✅ Complete

> **Status: Complete (2026-07-13)**

## Goal
Patient portal views use raw `axios` without an interceptor — no token refresh, no automatic 401 redirect. Staff side already has this in `api/request.ts` (Round 19). Replicate the pattern for patient views.

## Changes

### Backend
| File | Action | Description |
|------|--------|-------------|
| `PatientAuthController.java` | Modify | Add `generateRefreshToken()` method (30-day expiry, `scp: ["refresh"]`, separate issuer). Populate `refreshToken` at login (was `null`). Add `POST /api/v1/patient/refresh` endpoint — validates refresh token (Nimbus JWT parsing, scp/roles checks, expiry), returns new access+refresh token pair (rotation). Reuses `PatientLoginResponse` DTO |
| `SecurityConfig.java` | Modify | Add `/api/v1/patient/refresh` to permitAll chain alongside `/api/v1/patient/login` |

### Frontend
| File | Action | Description |
|------|--------|-------------|
| `api/patientRequest.ts` | **New** | Axios instance with `baseURL: '/api/v1'`. Request interceptor injects `Authorization: Bearer <patientToken>`. Response interceptor: on 401, POST `/api/v1/patient/refresh`, retry, queue concurrent requests. On failure, clear both tokens, redirect to `/patient/login`. **Does NOT unwrap `Result<T>`** — returns raw response so existing `r.data.data.xxx` patterns work unchanged |
| `patient/login/index.tsx` | Modify | Store `patientRefreshToken` from login response |
| `patient/layout/PatientLayout.tsx` | Modify | Clear `patientRefreshToken` on logout. Export download uses `patientRequest` instead of raw fetch |
| 7 patient views (`*.tsx`) | Modify | Replace `import axios` → `import patientRequest`, remove manual `Authorization` headers. Chat view keeps `token` variable for SSE and JWT parsing (not API calls) |

## Risk & Mitigations
- Refresh token rotation: each refresh invalidates the previous token. If a refresh succeeds but frontend crashes before persisting new tokens, the user logs out — same as staff pattern
- SSE token expiry: `useChatSse` uses the raw token for EventSource. If token expires mid-session, SSE reconnects with expired token (pre-existing limitation, out of scope)
- Staff `JwtClaimMapper` force-logout doesn't apply to patient tokens (pre-existing gap, out of scope)

---

# Round 23: Frontend Quality Cleanup ✅ Complete

> **Status: Complete (2026-07-08)**
> **Method: Workflow (Plan → Implement → Review), 3 agents, 130K tokens, 205s**

## Goal
Eliminate duplicated code, fix remaining `|| null` bug, standardize common patterns across all frontend views.

## Findings

### Duplicated constants
| Pattern | Files | Fix |
|---------|-------|-----|
| `STATUS_COLOR` (billing) | `billing/index.tsx:8`, `patient/bills/index.tsx:5` | Extract to `utils/labels.ts` |
| `statusColor` (appointments) | `appointments/index.tsx:30`, `patient/appointments/index.tsx:14` | Extract to `utils/labels.ts` |

### Lingering bug
| Pattern | File | Fix |
|---------|------|-----|
| `Number(it.duration) \|\| null` drops 0 | `prescriptions/index.tsx:86` | `it.duration !== '' ? Number(it.duration) : null` |
| `form[f] \|\| ''` drops 0/false | `system/users:40`, `system/roles:34`, `system/menus:29` | `form[f] ?? ''` |

### Native dialogs (7 files, ~9 instances)
| Pattern | Count | Issue |
|---------|-------|-------|
| `confirm('Delete?')` | 6 views | Inaccessible, no custom styling |
| `prompt('Denial reason:')` | 1 view | No validation |

**Decision**: keep `confirm()`/`prompt()` as project convention (CLAUDE.md-aligned). Only extract the duplicated constants and fix the `||` bugs.

### Hardcoded page size
All paginated views use `size: 10` and `page*10>=total`. Extract to `utils/constants.ts` as `PAGE_SIZE = 10`.

### Files to Modify
| File | Change |
|------|--------|
| `utils/labels.ts` | Add `BILL_STATUS_COLOR`, `APPOINTMENT_STATUS_COLOR` |
| `utils/constants.ts` | **New** — `export const PAGE_SIZE = 10` |
| `billing/index.tsx` | Import STATUS_COLOR from labels, use PAGE_SIZE |
| `patient/bills/index.tsx` | Import STATUS_COLOR from labels, use PAGE_SIZE |
| `appointments/index.tsx` | Import color function from labels, use PAGE_SIZE |
| `patient/appointments/index.tsx` | Import color function from labels, use PAGE_SIZE |
| `prescriptions/index.tsx` | Fix `\|\| null` → `!== '' ? Number() : null` |
| `system/users/index.tsx` | Fix `\|\| ''` → `?? ''` |
| `system/roles/index.tsx` | Fix `\|\| ''` → `?? ''` |
| `system/menus/index.tsx` | Fix `\|\| ''` → `?? ''` |

## Results
- **14 files changed** (11 modified, 0 new)
- **Review verdict**: Ready to merge — zero findings
- **Build**: passes, no new TypeScript errors
- **Behavior**: zero changes, pure refactor

---

# Round 23.1: Patient Appointment Self-Service ✅ Complete

> **Status: Complete (2026-07-08)**
> **Note: Skipped Workflow process (small change) — corrected with post-commit review agent**

## Goal
Patients can cancel their own appointments and book new ones. Previously the patient appointments page was read-only.

## Backend
| Endpoint | Auth | Description |
|----------|------|-------------|
| `PUT /api/v1/patient/me/appointments/{id}/cancel` | PATIENT | Cancel own appointment (ownership check, rejects already cancelled/completed) |

## Frontend
| File | Action | Description |
|------|--------|-------------|
| `views/patient/appointments/index.tsx` | Modify | Cancel button per row (hidden for cancelled/completed). try/catch error handling, page reset to 1 after action |

## Design Decision: Self-Booking Removed
Patient self-booking (`POST /patient/me/appointments`) was implemented then removed after clinical review:
- Patients cannot determine the appropriate doctor, visit type, slot duration, or insurance network
- Booking is a staff function requiring clinical triage and slot management
- Cancel is retained — patients should be able to cancel their own appointments

## Post-Commit Review Findings
| Severity | Issue | Fix |
|----------|-------|-----|
| HIGH | `handleBook` no try/catch — modal closed on failure | Wrapped in try/catch with alert (then removed with book feature) |
| MEDIUM | Missing `@Transactional` + `@Auditable` on cancel endpoint | Added annotations (retained) |
| MEDIUM | `refresh` stale closure on `page` variable | Changed to `fetchAppointments(p?)` (retained) |

## Verified
- Cancel appointment #202: status 0→2 (Scheduled→Cancelled) ✅
- Cancel already-completed appointment: 409 error ✅
- Cross-patient cancel: 403 error ✅

---

# Round 24: Consent Management UI ✅ Complete

> **Status: Complete (2026-07-09)**

## Goal
HIPAA-compliant consent management. Patients sign consents for data sharing, treatment, and research. Backend complete, frontend zero.

## Backend (already exists)
| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/v1/consent` | ADMIN,DOCTOR | Create consent record `{patientId, consentType, scope}` — originally ADMIN-only, opened to DOCTOR after review |
| `GET /api/v1/consent?patientId=` | ADMIN,DOCTOR | List consent records for a patient |
| `PUT /api/v1/consent/{id}/revoke` | ADMIN,DOCTOR | Revoke a consent |
| `GET /api/v1/patient/me/consent` | PATIENT | View own consent records |

## Plan

### Frontend
| File | Action | Description |
|------|--------|-------------|
| `api/consent.ts` | **New** | `createConsent`, `getConsents(patientId)`, `revokeConsent(id)` |
| `views/patients/ConsentTab.tsx` | **New** | Consent list + create form inside patient detail (or as a tab) |
| `views/patient/consent/index.tsx` | **New** | Patient portal: view own consents (read-only) |
| `App.tsx` | Modify | Add `/patient/consent` route |
| `PatientLayout.tsx` | Modify | Add Consent nav item |

### Consent Types (from existing Consent entity)
- `TREATMENT` — consent for treatment
- `RESEARCH` — consent for research use of data
- `DATA_SHARING` — consent to share data with other providers
- `MARKETING` — consent for marketing communications

### Scope
8 files changed (3 new, 5 modified). Backend fixes: `@Transactional` + `@Auditable` on create/revoke, `@NotNull` on patientId, permissions upgraded to ADMIN+DOCTOR. Patient portal: read-only consent list via `/patient/consent`.

### Post-Release
- Consent endpoints changed from ADMIN-only to ADMIN+DOCTOR — doctors need consent management for clinical workflows
- Audit logging confirmed: CREATE and REVOKE actions recorded (audit log table)
- Consent button visible to both ADMIN and DOCTOR on patient list

---

# Round 25: Emergency Access UI ✅ Complete

> **Status: Complete (2026-07-10)**

## Goal
Break-glass emergency access for clinical emergencies. A doctor who normally can't access a patient's record can initiate emergency access (30-min JWT), all audited. Backend complete, frontend zero.

## Backend (already exists)
| Endpoint | Auth | Description |
|----------|------|-------------|
| `POST /api/v1/emergency/access/{patientId}` | ADMIN,DOCTOR | Initiate emergency access → returns 30-min emergency JWT |
| `GET /api/v1/emergency/history` | ADMIN | List emergency access history, filter by `?patientId=` or `?audited=0` |
| `PUT /api/v1/emergency/{id}/review` | ADMIN | Mark access as reviewed (audited=1, reviewedBy, reviewedAt) |

## Changes

| File | Action | Description |
|------|--------|-------------|
| `api/emergency.ts` | **New** | `initiateEmergencyAccess`, `getEmergencyHistory`, `reviewEmergencyAccess` |
| `views/system/EmergencyAudit.tsx` | **New** | ADMIN audit page: table (id, userId, patientId, reason, accessedAt, expiresAt, audited, reviewedBy, reviewedAt), patientId filter, audited dropdown, Review button per row |
| `views/patients/index.tsx` | Modify | Break Glass button per row → reason prompt modal → POST → result modal (copyable token, expiresIn) |
| `App.tsx` | Modify | `/emergency` route with AdminGuard |
| `StaffLayout.tsx` | Modify | Emergency Access nav item (ADMIN only) |

## Revisions
- **Auto-redirect**: Token display removed — break-glass now auto-opens patient form via sessionStorage token injection. Medical staff never see a JWT.
- **Emergency prescriptions**: `GET /api/v1/prescriptions/by-patient/{patientId}` added. During break-glass, patient's active prescriptions (with items) are displayed in the form. Critical for unconscious/coma patients who cannot self-report medications.

## Results
- **8 files changed** (2 new, 6 modified), ~262 insertions
- **Workflow**: 3 agents, 124K tokens, 258s
- **Review verdict**: Ready to merge
- **Security**: Emergency token in sessionStorage (cleared after single use), audit page behind AdminGuard

---

# Round 26: Lab Results & LOINC Viewer ✅ Complete

> **Status: Complete (2026-07-10)**
> **Method: Workflow (Plan → Implement → Review), 3 agents, 115K tokens, 278s**

## Goal
Patients and doctors can view lab results with historical trends, LOINC-coded reference ranges, and abnormal flagging. Backend complete with 5 endpoints, seed data (7 observations, 29 LOINC codes).

## Changes

| File | Action | Description |
|------|--------|-------------|
| `api/observation.ts` | **New** | `getObservations(patientId, loinc?)`, `getLoincCatalog()`, `getLoincPanel(parentCode)` |
| `views/lab/LabResults.tsx` | **New** | Staff view: select patient → single summary table grouped by collection date (Test, Value, Unit, Ref Range, Flag with color) |
| `views/lab/LoincCatalog.tsx` | **New** | LOINC catalog browser: panel list → expand → individual tests with ref ranges |
| `views/patient/lab/index.tsx` | **New** | Patient portal: auto-loads all results, same table layout |
| `App.tsx` | Modify | `/lab`, `/loinc`, `/patient/lab` routes |
| `StaffLayout.tsx` | Modify | Lab Results + LOINC Catalog nav items (ADMIN,DOCTOR) |
| `PatientLayout.tsx` | Modify | Lab Results nav item |
| `PatientPortalController.java` | Modify | `GET /patient/me/observations?loinc=` (patient-accessible, loinc optional) |
| `Observation.java` | Modify | Extend BaseEntity (+soft delete, +@Version) |
| `schema.sql` | Modify | Add is_deleted, version, update_time to observation table |

## Results
- **10 files changed** (4 new, 6 modified), 401 insertions
- **Review**: Blocked initially — Observation entity didn't extend BaseEntity. Fixed before merge.
- **Post-release**: Redesigned from multi-tier drill-down to single summary table

---

# Round 27: Audit Log Viewer ✅ Complete

> **Status: Complete (2026-07-10)**
> **Method: Workflow (Plan → Implement → Review), 3 agents, 118K tokens, 133s**

## Goal
Admin-only audit log viewer with filtering by module, action, userId, patientId, and date range.

## Changes

| File | Action | Description |
|------|--------|-------------|
| `api/audit.ts` | **New** | `getAuditLogs(params)` → `GET /audit-logs` with all filter params |
| `views/system/AuditLogs.tsx` | **New** | Filter bar (module, action, userId, patientId, fromDate, toDate) + paginated table (10 columns + PageInfo total) |
| `App.tsx` | Modify | `/audit-logs` route with AdminGuard |
| `StaffLayout.tsx` | Modify | Audit Logs nav item (ADMIN only) |

### Post-release: Audit Log Username Fix
`AuditLogAspect` now extracts username from request body via reflection when SecurityContext is null (e.g. login). Previously `auth/LOGIN_SUCCESS` had `username=NULL`.

## Results
- **5 files changed** (2 new, 3 modified), 166 insertions
- **Review**: Ready to merge

---

### Post-Round 27: Menus Page Read-Only
Removed create/edit/delete from `/system/menus`. Menu structure is defined in code (`StaffLayout.tsx` + routes), not driven by database. Page now shows tree with indentation, type, and sort order.

---

# Round 28: Clinical Data Immutability ✅ Complete

> **Status: Complete (2026-07-13)**
> **Method: Workflow (Plan → 4 parallel pipeline agents → Review), 6 agents, 189K tokens, 120s**
> **9 files changed**

## Principle
Medical decisions are historical facts. They should only be **terminated/cancelled** or **corrected with new entries**, never edited in place. An edit that silently overwrites clinical data destroys the audit trail and creates medico-legal risk.

## Audit Results

| Module | Put Endpoints | Issue | Severity |
|--------|-------------|-------|----------|
| **Billing** | adjudicate, pay, deny | Insurance payment/adjustment figures overwritten; PAID bills can be re-adjudicated; DRAFT can bypass adjudication via pay(); no version history | **CRITICAL** |
| **Patient Records** | PUT /{id} | Medical history + allergies overwritten in place; name/DOB/sex edits retroactively alter all historical records; Edit button on every row | **CRITICAL** |
| **Appointments** | PUT /{id} | Terminal states (2/3/4) are editable — completed visits can be retroactively changed | **CRITICAL** |
| **Prescriptions** | PUT /{id} | Legal medical order can be fully edited after signing; bypasses CDS re-check; should be cancel-reissue | **CRITICAL** |
| **Chat** | None | Append-only — messages cannot be edited or deleted | COMPLIANT |
| **Sys Users** | PUT /{id} | Staff operational data; DEA/license changes unversioned but acceptable | LOW |
| **Sys Roles** | PUT /{id} | RBAC configuration only — no clinical data | LOW |

---

## Part A: Appointments — Terminal State Edit Protection

### Analysis
Completed (3), Cancelled (2), and No-Show (4) are terminal states — the visit outcome is a medico-legal record. Editing them destroys the clinical audit trail and is fraud-relevant (e.g., changing "no-show" to "arrived" after the fact).

Only transitional states should be editable: Scheduled (0), Arrived (1), Rescheduled (5), In Progress (6).

### Fix
| Layer | File | Change |
|-------|------|--------|
| Backend | `AppointmentService.update()` | Status guard: if status ∈ {2,3,4} → 409 "Terminal appointments cannot be modified" |
| Frontend | `appointments/index.tsx` | Hide Edit button when status ∈ {2,3,4} |

---

## Part B: Prescriptions — Cancel-Reissue Instead of Edit

### Analysis
A prescription is a legal medical order. Editing it in place:
- Destroys the original order (no record of what was first prescribed)
- Bypasses CDS re-check (drug interactions, allergy warnings)
- If wrong drug/dosage was prescribed, correct workflow is: cancel old → create new

### Fix
| Layer | File | Change |
|-------|------|--------|
| Frontend | `prescriptions/index.tsx` | Remove Edit button. Add Cancel button for active Rx (status→cancelled) |
| Backend | `PrescriptionController` | Add `PUT /{id}/cancel` — ADMIN,DOCTOR. Sets rxStatus="cancelled" |

---

## Part C: Billing — Immutable State Transitions

### Analysis
Billing is a regulated financial transaction. The `adjudicate` endpoint overwrites insurance payment, adjustment, and patient responsibility figures on the Bill row. A PAID bill can be re-adjudicated with different amounts — the original figures are silently lost. The `pay` endpoint allows DRAFT bills to bypass adjudication entirely.

### Fix
| Layer | File | Change |
|-------|------|--------|
| Backend | `BillService.adjudicate()` | Add guard: reject if status is already PAID or DENIED |
| Backend | `BillService.pay()` | Remove DRAFT from payable states (must go through submit→adjudicate first) |
| Backend | `BillService.deny()` | Add guard: reject if already PAID (cannot deny a paid bill) |
| Frontend | `billing/index.tsx` | Hide Adjudicate button for PAID/DENIED; hide Deny for PAID; hide Pay for PAID/DENIED (already partially done — verify) |

---

## Part D: Patient Records — Append-Only Clinical Data

### Analysis
The patient edit form allows direct overwrite of `medicalHistory` and `allergies` — clinical facts that should be append-only. Name/DOB/sex at birth edits retroactively change all historical records (past encounters now show the new name). For a production system, these need versioned demographics + append-only clinical entries. For the current scope, removing these fields from the edit form and making them staff-managed via dedicated append operations is the minimal fix.

### Fix (Immediate — current scope)
| Layer | File | Change |
|-------|------|--------|
| Frontend | `patients/index.tsx` | Mark `name`, `mrn`, `ssn`, `dateOfBirth`, `sexAtBirth`, `medicalHistory`, `allergies` as readonly in edit form (display-only, not editable) |
| Backend | `PatientService.update()` | Ignore or reject attempts to modify `medicalHistory` and `allergies` through the general update endpoint |

### Future (production scope — deferred)
- ~~`medicalHistory` and `allergies` → append-only entities~~ ✅ Done (Round 29)
- Patient demographics → versioned records with effective dates
- Name/DOB changes → dedicated workflow with audit trail

---

## Post-Round 28: Agent Roles Reorganization + Round 22 Re-Review

### Agent Configs Moved to CLAUDE.md
Merged 4 `.claude/agents/*.md` files into root `CLAUDE.md` as a dedicated **Agent Roles** section. Plan and Review phases are now done directly (not via subagents) for deep reasoning quality. Only Implement may use subagents for mechanical edits.

### Round 22 Re-Review (Opus)
Re-reviewed with Opus model. Found and fixed 3 critical/high + 4 medium/low issues:
- **CRITICAL**: refresh endpoint signature verification (forged tokens now rejected)
- **HIGH**: `changePassword()` @Transactional, JWT `jti` uniqueness
- **MEDIUM**: `updateProfile()` + `changePassword()` @Auditable annotations
- **LOW**: refresh token expiry externalized to configuration

---

# Round 29: Append-Only Medical History & Allergies ✅ Complete

> **Status: Complete (2026-07-13)**
> **Method: Plan + Implement done directly (not subagent), 9 files**

## Goal
From Round 28 Part D deferred items: make `medicalHistory` and `allergies` append-only entities with timestamps and provider attribution, instead of mutable text fields on the Patient record.

## Changes

| File | Action | Description |
|------|--------|-------------|
| `MedicalHistoryEntry.java` | **New** | Entity: patientId, description, recordedBy. Extends BaseEntity |
| `AllergyEntry.java` | **New** | Entity: patientId, allergen, reaction, severity, recordedBy. Extends BaseEntity |
| `MedicalHistoryEntryRepository.java` | **New** | JPA + findByPatientIdOrderByCreateTimeDesc |
| `AllergyEntryRepository.java` | **New** | JPA + findByPatientIdOrderByCreateTimeDesc |
| `PatientController.java` | Modify | GET/POST `/patients/{patientId}/history`, GET/POST/DELETE `/patients/{patientId}/allergies` with @Transactional, @Auditable, ownership checks |
| `DataInitializer.java` | Modify | Seed 4 allergy entries for patients 100, 101 |
| `schema.sql` | Modify | 2 new tables (medical_history_entry, allergy_entry) |
| `api/patient.ts` | Modify | 5 new API functions |
| `patients/index.tsx` | Modify | Replace medicalHistory/allergies text inputs with append-only entry lists + add-entry forms |

## Verified
- Seed allergies: Penicillin(SEVERE) + Shellfish(MODERATE) for patient 100 ✅
- POST history: recordedBy captures userId ✅
- POST allergy: allergen/reaction/severity all stored ✅

### Post-Release Revisions

**Allergy resolution (was: silent delete)** — Changed `DELETE /allergies/{id}` to `PUT /allergies/{id}/resolve`. Sets `status=resolved`, records `resolvedBy` + `resolvedAt`. Frontend shows resolved allergies greyed out with strikethrough + resolution date. Re-revoke blocked (409).

**Row click to view patient** — Removed View button. Clicking a patient row opens read-only view mode with history + allergies. Action buttons use `e.stopPropagation()`. Added `.clickableRow` CSS class with `:hover` blue background + `cursor:pointer`.

---

# Round 30: HIPAA Compliance Remediation ✅ Complete

> **Status: Complete (2026-07-14)**
> **Method: Pro model review of 29 controllers, 22 entities, 11 services**
> **Method: Pro model review of all 29 controllers, 22 entities, 11 services**

## Audit Scope
Systematic review of all backend code for: @PreAuthorize coverage, @Auditable on CUD/PHI access, @Transactional on mutations, entity integrity (BaseEntity, @SQLDelete, @Version), PHI encryption, ownership verification, token security.

## Findings

### 🔴 CRITICAL

| # | File | Issue | Risk |
|---|------|-------|------|
| 1 | `ChatService.sendMessage()` | Missing `@Auditable` | Patient-doctor PHI communication has no audit trail |
| 2 | `UserProfileController.changePassword()` | Missing `@Transactional` + `@Auditable` | PasswordHistory + SysUser written outside transaction — inconsistent state on partial failure |
| 3 | `UserProfileController.updateProfile()` | Missing `@Transactional` | Has `@Auditable` but mutation not in transaction boundary |

### 🟡 HIGH

| # | Entity | Issue |
|---|--------|-------|
| 4 | `EmergencyAccess` | Does not extend BaseEntity — no soft-delete, no @Version |
| 5 | `PasswordHistory` | Does not extend BaseEntity — security audit data |
| 6 | `CdsOverride` | Does not extend BaseEntity — clinical override records |

### 🟠 MEDIUM

| # | File | Issue |
|---|------|-------|
| 7 | `PatientPortalController.updateProfile()` | Has `@Auditable` but no `@Transactional` |
| 8 | 5 reference entities | `DrugInteraction`, `DrugAllergyClass`, `LoincCatalog`, `PharmacyDirectory`, `QualityMeasure` do not extend BaseEntity — reference/lookup data, acceptable but should be unified |

### ✅ VERIFIED

| Category | Result |
|----------|--------|
| @PreAuthorize on endpoints | All 29 controllers checked — public endpoints (auth/login, patient/login, patient/refresh) correctly in SecurityConfig permitAll. No unauthorized endpoints found. |
| Controllers delegate to Services | All CUD controllers delegate @Transactional + @Auditable to service layer correctly (Appointment, Billing, Prescription, Patient, SysUser, SysRole, SysMenu, Consent, Emergency). |
| Patient-owned resource verification | All patient portal endpoints verify `loginUser.getUserId()` matches resource owner. |
| Token security | Patient refresh uses JwtDecoder (signature verified). JWT jti uses UUID. Staff refresh dev-mode restriction documented. |
| PHI encryption | `@Convert(converter = AesAttributeConverter.class)` on all patient entity PHI fields. `AesCryptoUtil.encrypt()` used in DataInitializer seed data. |
| Soft-delete coverage | All clinical entities extend BaseEntity with `@SQLDelete`/`@SQLRestriction`. |
| @Version optimistic locking | All BaseEntity-extending entities have `@Version` via inheritance. |

## Results
- **3 CRITICAL fixed**: ChatService @Auditable, UserProfileController @Transactional/@Auditable
- **3 HIGH fixed**: EmergencyAccess, PasswordHistory, CdsOverride now extend BaseEntity
- **2 MEDIUM fixed**: PatientPortalController.updateProfile() @Transactional
- **8 reference entities** (DrugInteraction, DrugAllergyClass, LoincCatalog, PharmacyDirectory, QualityMeasure) intentionally kept without BaseEntity — lookup/reference data with no PHI

---

# Round 31: Frontend Data Freshness ✅ Complete

> **Status: Complete (2026-07-14)**
> **Method: Subagent Implement — 1 agent, 56K tokens, 127s**
> **15 files changed, 45 insertions**

## Problem
Multiple pages don't automatically refresh data when navigated to. Data is stale from the previous visit until the user manually switches pages and comes back. This affects:
- **Audit Logs**: filter/search results don't re-fetch on page load
- **All list pages**: Patients, Appointments, Prescriptions, Billing show stale data after CRUD operations in other views
- **Dashboard**: stats don't update when navigating back

Root cause: `useEffect(fetchData, [])` with empty dependency only fires on initial component mount. React Router reuses the same component instance on re-navigation, so the effect doesn't re-run.

## Plan

| File | Change |
|------|--------|
| All paginated list views | Add `useLocation()` dependency to `useEffect` to re-fetch on route change |
| Dashboard | Add `useLocation()` trigger |
| Audit Logs, Emergency Audit | Add `useLocation()` trigger |
| Patient portal list views | Same fix |

Alternative considered: React Router's `<Outlet context>` or Redux — rejected as overengineered for current scope.

### Scope
~15 files, ~2 lines each (import `useLocation`, add `location` to useEffect deps).

---

# Round 32: Audit Log Detail View ✅ Complete

> **Status: Complete (2026-07-14)**
> **2 files changed, 23 insertions**

## Problem
Audit log table shows 10 columns (ID, User, Username, Patient, Module, Action, Target, Detail, IP, Timestamp) but the `detail` column contains method parameter dumps (e.g. `login(LoginRequest(username=admin, password=[PROTECTED]))`) that are truncated in the table. Currently rows are not clickable — there's no way to see the full detail text.

## Plan
| File | Change |
|------|--------|
| `views/system/AuditLogs.tsx` | Add row click → expand inline or show modal with full detail. Truncated detail preview in table, click to see full text. |
| `views/system/EmergencyAudit.tsx` | Same pattern — click row to see full reason text |

### Scope
2 files, ~30 lines each. Small round.

---

# Round 33: React Query — Client-Side Caching & Optimistic Updates ✅ Complete

> **Status: Complete (2026-07-15) — 17 files migrated, all `useLocation` deps removed, verified with Vite build**

## Problem
Round 31's `useLocation()` trigger re-fetches ALL data on every navigation. At scale, this wastes bandwidth and server resources. No client-side caching, deduplication, or stale-while-revalidate.

## Industry Standard
**React Query (TanStack Query)** is the dominant solution in production healthcare UIs (Epic MyChart, Cerner, Athenahealth):
- **Write**: `useMutation` with optimistic update — UI updates instantly, server confirms in background
- **Read**: `useQuery` with stale-while-revalidate — show cached data immediately, re-fetch in background
- **Deduplication**: multiple components requesting same key → single network call
- **Window refocus**: auto-refresh when user switches tabs
- **Retry**: automatic exponential backoff on failure
- **Pagination/infinite scroll**: built-in support

## Plan
| Task | Description | Status |
|------|-------------|--------|
| Add `@tanstack/react-query` dependency | ~15kB gzipped, zero-config | ✅ |
| Wrap App with `QueryClientProvider` | `main.tsx` — single provider at root | ✅ |
| Migrate Appointments page (POC) | `useQuery` for list, `useMutation` for CUD, remove `useLocation` | ✅ |
| Migrate remaining staff paginated views | `billing`, `system/users`, `system/roles` | ✅ |
| Migrate staff non-paginated views | `dashboard`, `system/menus`, `system/EmergencyAudit`, `system/AuditLogs`, `profile` | ✅ |
| Migrate complex staff views | `prescriptions`, `patients` — main list only, sub-queries kept imperative | ✅ |
| Migrate patient views | 7 views with `patientRequest` unwrapping in queryFn | ✅ |
| Remove `useLocation()` deps everywhere | React Query `refetchOnWindowFocus` replaces navigation trigger | ✅ |

## Query Key Convention
```
[resource, scope, ...params]

Scopes: 'list' (paginated), 'detail' (single by ID), 'sub' (sub-resource),
        'all' (full dataset), 'tree' (hierarchical), 'stats' (dashboard)

Patient self-data prefixed with 'me':
  ['me', resource, scope, ...params]   // e.g. ['me', 'appointments', 'list', { page, size }]
```

### QueryClient Config
```ts
staleTime: 30_000, gcTime: 5 * 60_000, refetchOnWindowFocus: true, retry: 2
```

## All Files Changed (17 files)
| File | Change |
|------|--------|
| `package.json` | Added `@tanstack/react-query` |
| `src/main.tsx` | Added `QueryClientProvider` with config |
| `views/appointments/index.tsx` | `useQuery` + `useMutation` + removed `useLocation` |
| `views/billing/index.tsx` | 6 mutations (create/submit/adjudicate/pay/deny/delete), `patients` dropdown via `useQuery` |
| `views/system/users/index.tsx` | `useQuery` + `useMutation` + removed `useLocation` |
| `views/system/roles/index.tsx` | `useQuery` + `useMutation` + removed `useLocation` |
| `views/dashboard/index.tsx` | `useQuery(['dashboard', 'stats'])`, removed `useLocation` |
| `views/system/menus/index.tsx` | `useQuery(['menus', 'tree'])` with 5min staleTime |
| `views/system/EmergencyAudit.tsx` | `useQuery` with search filter state, `useMutation` for review |
| `views/system/AuditLogs.tsx` | `useQuery` with `searchParams` (page+filters), distinct values as separate query |
| `views/profile/index.tsx` | `useQuery(['profile'])` + `useMutation` for update/password |
| `views/prescriptions/index.tsx` | `useQuery` for list + 5 mutations, CDS/pharmacy/RxNorm kept imperative |
| `views/patients/index.tsx` | `useQuery` for list + 3 mutations, sub-resources kept imperative |
| `views/patient/appointments/index.tsx` | Patient pattern: `patientRequest.then(r => r.data.data)`, `['me', ...]` keys |
| `views/patient/prescriptions/index.tsx` | Patient pattern, read-only |
| `views/patient/bills/index.tsx` | Patient pattern + pay mutation |
| `views/patient/profile/index.tsx` | Patient pattern + update/password mutations |
| `views/patient/consent/index.tsx` | Patient pattern, `isLoading` for loading state |
| `views/patient/lab/index.tsx` | Patient pattern, `isLoading` for loading state |
| `views/patient/dashboard/index.tsx` | 3 parallel `useQuery` calls replacing `Promise.all` |

## Skipped (by design)
| File | Reason |
|------|--------|
| `chat/index.tsx` + `patient/chat/index.tsx` | SSE-driven, not query-based |
| `lab/LabResults.tsx` + `LoincCatalog.tsx` | Event-driven / static data |
| `login/index.tsx` + `patient/login/index.tsx` | Auth mutations, no data queries |

## Backend Fixes (included in this round)
- Removed dead `authenticationManager` bean causing `StackOverflowError` on login (self-referential `ProviderManager` proxy)
- Widened `audit_log.action` from `VARCHAR(20)` to `VARCHAR(50)` (`PATIENT_TOKEN_REFRESH` = 21 chars)
- Updated `schema.sql` + `AuditLog.java` entity

## Verified
- `npm run build` passes (343KB JS, 186 modules)
- Backend starts without StackOverflowError
- Login + authenticated endpoints work
- All `useLocation` imports removed from data-fetching views

## Risk
- New npm dependency (`@tanstack/react-query`). CLAUDE.md requires "no new dependencies without concrete justification." This justifies: 15 files stop re-fetching unconditionally, server load drops significantly, user experience improves.
- Migration is file-by-file, can be done incrementally.

## Scope
~15 view files + 1 provider in main.tsx + 1 new dependency. Medium round.

---

# Round 34: Audit Fixes & Gap Closure ✅ Complete

> **Status: 6 of 6 gaps resolved (2026-07-16)**

## Gap 1 🔴 Patient Export Data Contract Mismatch ✅ Fixed

**Severity**: HIGH — broken UX
**Fix**: Frontend now parses JSON response correctly: extracts `res.data.data` (the PatientDataExport object), formats as indented JSON, downloads as `health-data-YYYY-MM-DD.json`.
**Commit**: `e3f4fa0`

## Gap 2 🔴 Prescription In-Place Edit Endpoint Still Alive ✅ Fixed

**Severity**: HIGH — clinical risk
**Fix**: Removed `PUT /api/v1/prescriptions/{id}` endpoint, `PrescriptionService.update()`, and `PrescriptionUpdateFormDTO`.
**Commit**: `6df8035`

## Gap 3 🟡 eCQM Quality Measures — No Frontend ✅ Fixed

**Severity**: MEDIUM — functional gap
**Fix**: Created `api/quality.ts`, `views/system/QualityMeasures.tsx` with measure list + report panel. Route `/system/quality` under AdminGuard. Sidebar link under admin section.
**Commit**: `5bec26d`

## Gap 4 ⚪ `QualityResult` Entity Missing ✅ Fixed

**Severity**: LOW — data model inconsistency
**Problem**: ROADMAP Round 9 mentions `quality_result` table and `QualityResult` entity. Neither exists in `schema.sql` or codebase. `QualityMeasureService.calculateReport()` returns `HashMap` instead of a persisted entity.
**Fix**: Created `quality_result` table + `QualityResult` entity, `persistResult()` saves calculation results, `getHistory()` reads persisted history (QualityController history endpoint). Report endpoint returns persisted entity.
**Commit**: `d65130b`

## Gap 5 ⚪ Round 17-10 Permission Change Not Landed ✅ Resolved (by design)

**Severity**: LOW — documentation mismatch
**Decision**: Keep `hasRole()`/`hasAnyRole()` — this is the standard Spring Security RBAC pattern. The menu `permission` codes (`system:user:list`, `patient:list`, etc.) are used for frontend menu visibility filtering, not backend authorization. Converting to `hasAuthority()` would add complexity with no security benefit — roles already map cleanly to clinical access levels (ADMIN = full access, DOCTOR = clinical access, PATIENT = self-service).

## Gap 6 ⚪ Integration DTO Pattern Inconsistency ✅ Fixed

**Severity**: LOW — style deviation
**Fix**: Renamed `AdtEventDTO` → `AdtEventPayload`, `LabResultDTO` → `LabResultPayload`. These are inbound JSON message schemas (Mirth Connect integration), not JPA entity-conversion DTOs. The `Payload` suffix clearly distinguishes them from `fromEntity()`/`toEntity()` DTOs.
**Commit**: see git log

## Execution Priority
1. Gap 2 (Prescription endpoint) — lowest effort, highest clinical risk
2. Gap 1 (Patient export) — user-facing bug
3. Gap 3 (eCQM frontend) — new feature, most effort
4. Gaps 4-6 — documentation/low-priority cleanup

---

# Round 35: Second Audit — Security & Data Integrity Fixes ✅ Complete

> **Status: Complete (2026-07-17) — all 12 gaps resolved + 3 additional fixes**

## 🔴 Critical — All Fixed

| Gap | Issue | Fix | Commit |
|-----|-------|-----|--------|
| 7 | phone_work column width | Already VARCHAR(200) — verified no risk | - |
| 8 | Frontend updatePrescription 404 | Removed from api/prescription.ts + prescriptions page | fb6553e |
| 9 | JWT/AES key sharing | JWT_SIGNING_KEY separate from AES_KEY | 701296c |
| 10 | Patient login rate limit | Already active — verified 10 req/min → 429 | - |

## 🟡 High — All Fixed

| Gap | Issue | Fix | Commit |
|-----|-------|-----|--------|
| 11 | Encrypted email LIKE search | Removed email from database-level keyword search | bd2964a |
| 12 | Past appointment cancel | Guard + AppointmentScheduler auto no-show | 4fb5b0a, bbaa8b7 |
| 13 | Account unlock | PUT /users/{id}/unlock + UI button + LockoutService REQUIRES_NEW | 18b44bc, b64bbf9, 59c626b |
| 14 | lastLoginTime | Added column + entity + VO + AuthService login tracking | c0206c4 |
| 15 | CORS empty origin | Filter blank origins, fallback to localhost:5173 | 36081f2 |
| 16 | Integration auth | X-Integration-Key header check + dev default key | e1076c4 |

## ⚪ Low — All Resolved

| Gap | Issue | Fix | Commit |
|-----|-------|-----|--------|
| 17 | Reference entities BaseEntity | Documented intentional exception in CLAUDE.md | c842dc8 |
| 18 | Bill prompt() dialogs | Modal forms with validation + isPending | 9c6609d |

## Additional Fixes Beyond Audit

- Admin cannot delete own account (409 + frontend hides Del button) — e0cdbf1, f7181d7
- Account lockout fixed (transaction rollback was reverting failedAttempts) — 59c626b
- JPA persistence context staleness in lockout queries (clearAutomatically=true) — 4a1130c

---

# Round 36: Comprehensive Gap Analysis (Audit)

> **Status: Audit complete (2026-07-27) — 22 gaps identified, 21 resolved, 1 deferred. All HIGH/MEDIUM gaps closed. Only 3 LOW gaps (B10/B12/B13) deferred.**
> **Method: Full-stack review of 84 backend endpoints vs 70 frontend API functions vs UI views, plus US healthcare feature completeness audit**

## Gap Categories

### A. Backend Endpoints Missing Frontend

| # | Endpoint | Severity | Description |
|---|----------|----------|-------------|
| A1 | `POST /api/v1/auth/logout` | 🔴 HIGH | ✅ Fixed — `logout()` added to `api/auth.ts`, StaffLayout calls it before clearing localStorage. Best-effort fire-and-forget. |
| A2 | `GET /api/v1/export/patients` | 🔴 HIGH | ✅ Fixed — `api/export.ts` created, StaffLayout sidebar has Export CSV → Patients button. |
| A3 | `GET /api/v1/export/bills` | 🟡 MEDIUM | ✅ Fixed — `api/export.ts` created, StaffLayout sidebar has Export CSV → Bills button. |
| A4 | `GET /api/v1/admin/keys/history` | 🟡 MEDIUM | ✅ Fixed — `api/key.ts` created, AdminKeys page with key lifecycle table. |
| A5 | `POST /api/v1/admin/keys/rotate` | 🟡 MEDIUM | ✅ Fixed — AdminKeys page with rotate modal (oldKey/newKey form). |
| A6 | `GET /api/v1/admin/keys/rotation-status` | ⚪ LOW | ✅ Fixed — AdminKeys page with rotation status panel (active/running/complete/remainingByTable). |
| A7 | `GET /api/v1/patients/{id}/case` | 🟡 MEDIUM | ✅ Dead code removed — `getPatientCase()` deleted from `api/patient.ts`. Backend endpoint still exists, frontend to be added if needed. |
| A8 | `GET /api/v1/bills/{id}` | ⚪ LOW | ✅ Dead code removed — `getBillById()` deleted from `api/bill.ts`. Backend endpoint still exists. |
| A9 | `GET /api/v1/bills` 无 patientId 过滤 + DOCTOR 可看全量 | 🔴 HIGH | ✅ Fixed — `patientId` query param added. DOCTOR scoped to own patients via Appointment+Prescription union. Frontend patient dropdown filter. |

### B. US Healthcare Clinical Feature Gaps

| # | Feature | Severity | Current State |
|---|---------|----------|---------------|
| B1 | **Immunizations** | 🔴 HIGH | ✅ Fixed — `Immunization` entity (CVX code, date, lot#, manufacturer, dose, site/route). Staff CRUD, patient portal view. 9 seed records. |
| B2 | **Vital Signs** | 🔴 HIGH | ✅ Fixed — `VitalSign` entity (BP/HR/temp/RR/O₂/BMI). Staff CRUD, patient portal view. 4 seed records. |
| B3 | **Problem List / Diagnoses** | 🔴 HIGH | ✅ Fixed — `Problem` entity (SNOMED CT + ICD-10 coded). Staff CRUD with resolve, patient portal view. 8 seed records. |
| B4 | **Care Plans** | 🟡 MEDIUM | ✅ Fixed — `CarePlan` entity (title, goal, interventions, dates). Staff patient detail tab, patient portal view. 3 seed records. |
| B5 | **Referral Management** | 🟡 MEDIUM | ✅ Fixed — `Referral` entity with status workflow. Staff CRUD with Schedule/Complete/Close transitions. Patient portal view. 4 seed records. |
| B6 | **Superbill / Charge Capture** | 🟡 MEDIUM | ✅ Fixed — `Charge` entity linked to appointment. Staff `/charges` page with Convert to Bill button. 2 seed records. |
| B7 | **Prior Authorization** | 🟡 MEDIUM | ✅ Fixed — `PriorAuth` entity. Staff `/prior-auths` page with approve/deny workflow. Patient portal view. 2 seed records. |
| B8 | **Drug Formulary Checking** | 🟡 MEDIUM | ✅ Fixed — `FormularyEntry` lookup table. `GET /formulary/check?rxnormCode=&insurancePayer=`. 10 seed entries across BCBS/Aetna/UHC. |

### C. Patient Engagement Gaps

| # | Feature | Severity | Current State |
|---|---------|----------|---------------|
| B9 | **Prescription Refill Requests** | 🟡 MEDIUM | ✅ Fixed — `RefillRequest` entity. Patient creates request, doctor approves/denies. Buttons on both staff and patient prescription pages. |
| B10 | **New Patient Self-Registration** | ⚪ LOW | Deferred — current staff-created flow sufficient for clinic use case. |
| B11 | **HIPAA Accounting of Disclosures** | 🟡 MEDIUM | ✅ Fixed — `GET /patient/me/disclosures` queries audit_log by patientId. Patient portal `/patient/disclosures` page. |
| B12 | **Advance Directives** | ⚪ LOW | Deferred — living will/DNR/DNI primarily needed for inpatient settings, not outpatient. |
| B13 | **Appointment Reminders** | ⚪ LOW | Deferred — requires SMS/email infrastructure not in current stack. |

### D. Frontend Dead Code & Missing Modules

| # | Issue | Detail |
|---|-------|--------|
| D1 | `api/patient.ts` exports `getPatientCase` | ✅ Fixed — removed from `api/patient.ts`. |
| D2 | `api/bill.ts` exports `getBillById` | ✅ Fixed — removed from `api/bill.ts`. |
| D3 | `api/export.ts` does not exist | ✅ Fixed — created with `downloadPatientsCsv()` + `downloadBillsCsv()`. |
| D4 | `api/key.ts` does not exist | ✅ Fixed — created with `getKeyHistory()`, `rotateKey()`, `getRotationStatus()`. |
| D5 | `api/auth.ts` missing `logout()` | ✅ Fixed — `logout()` added, called by StaffLayout with best-effort try/catch. |

## Intentionally Backend-Only (Not Gaps)

| Endpoint | Reason |
|----------|--------|
| `POST /api/v1/integration/adt` | Mirth Connect integration engine → backend |
| `POST /api/v1/integration/lab-results` | Mirth Connect integration engine → backend |
| `GET /api/v1/fhir/Patient/{id}` | FHIR interoperability — external EHRs |
| `GET /api/v1/fhir/Patient?_id=` | FHIR interoperability — external EHRs |
| `GET /api/v1/fhir/Observation/{id}` | FHIR interoperability — external EHRs |
| `GET /api/v1/fhir/Observation?patient=` | FHIR interoperability — external EHRs |
| `GET /api/v1/fhir/metadata` | FHIR CapabilityStatement — SMART on FHIR discovery |

## Execution Priority

```
Priority 1 — Quick wins (logout fix, dead code cleanup)            ✅ Complete
Priority 2 — Staff export UI + Key management UI                   ✅ Complete
Priority 3 — Vital Signs + Problem List entities                   ✅ Complete
Priority 4 — Immunizations entity                                  ✅ Complete
Priority 5 — Refill requests + Accounting of Disclosures           ✅ Complete
Priority 6 — Referrals + Superbill + Care Plans + PA + Formulary   ✅ Complete
──────────────────────────────────────────────────────────────────
Deferred — B10 (self-registration), B12 (advance directives),
           B13 (appointment reminders) — all LOW priority,
           require external infra or inpatient context
```

## Post-Round 36: Superbill Auto-Fill (2026-07-24)

| # | Issue | Fix | Commit |
|---|-------|-----|--------|
| Fix | Superbill form required manual entry of all fields | Select patient → loads appointments → select appointment auto-fills CPT, ICD-10 hint (from chief complaint), visit type, suggested fee | `04f5570` |
| ⚠️ Note | Appointment lacks `icd10_codes` field | `chiefComplaint` is free-text, not ICD-10 codes. Auto-filled ICD-10 needs manual correction. Future: add `icd10_codes` column to `appointment` table and seed data. | — |

## Post-Round 36: Appointment Fixes (2026-07-23)

| # | Issue | Fix | Commit |
|---|-------|-----|--------|
| Bug | Past appointments stay "scheduled" after server restart | `AppointmentScheduler` now runs `markNoShows()` on `ApplicationReadyEvent` startup, not just 1am cron | `6f75b57` |
| Bug | Cancel button shown for no-show appointments | Patient portal `canCancel()` now excludes status 4 (no-show) alongside 2/3 | `77db804` |
| Bug | Doctor/admin can book past appointment times | `AppointmentService.create/update()` reject `appointmentTime < now` with 400. Frontend `datetime-local` gets `min={now}` | `7025aad` |

---

# Round 37: Frontend US Healthcare Standards Review (Audit)

> **Status: Audit complete (2026-07-28) — 49 findings across 30+ files, 0 implemented**
> **Method: 4-agent parallel review of clinical, patient portal, admin/feature views, and infrastructure**

## 🔴 CRITICAL — Security & Compliance

| # | Issue | Location |
|---|-------|----------|
| C1 | ✅ Token refresh `_retry=true` added | `request.ts`, `patientRequest.ts` |
| C2 | ✅ Password fields type=password | `AdminKeys.tsx` |
| C3 | ✅ CDS error now blocks save (was silent bypass) | `prescriptions/index.tsx` |
| C4 | EPCS controlled substance: no 2FA/DEA verification (backend needed) | prescriptions page |
| C5 | ✅ Chat SSE JWT in URL query string — replaced with single-use 30s ticket exchange (Round 38) | `useChatSse.ts` |
| C6 | ✅ Password confirm field + match/length validation | `patient/profile/index.tsx`, `profile/index.tsx` |
| C7 | ✅ Payment amount validation (positive, ≤balance) | `patient/bills/index.tsx` |
| C8 | ✅ Loading states (isLoading check) added to all views | users, roles, referrals, priorAuths, adminKeys, EmergencyAudit, patients, charges |
| C9 | ✅ onError handlers added to all mutations, passes through server message | global |

## 🟡 HIGH — UX & Data Quality

| # | Issue | Location |
|---|-------|----------|
| H1 | ✅ 429 rate limit handling | `request.ts`, `patientRequest.ts` |
| H2 | ✅ 30min idle auto-logout (useIdleTimeout hook) | StaffLayout, PatientLayout |
| H3 | ✅ Login lockout feedback (interceptor extracts server message) | login pages |
| H4 | ✅ confirm()/prompt()/alert() → ConfirmDialog modal system | 16 calls across all views |
| H5 | ✅ Doctor dropdown (GET /users/doctors endpoint, DOCTOR-accessible) | appointments, prescriptions |
| H6 | ✅ Patient names displayed instead of raw IDs | refill requests, draft charges |
| H7 | ✅ Form validation (patient/doctor/time required, totalCharge>0) | appointments, prescriptions, billing |
| H8 | ✅ patientRequest interceptor unwraps res.data.data | `patientRequest.ts` + 14 patient views |
| H9 | ✅ Patient logout endpoint + frontend call | `PatientAuthController`, `PatientLayout.tsx` |
| H10 | ✅ Delete hidden for transmitted/dispensed/cancelled Rx | `prescriptions/index.tsx` |
| H11 | ✅ Appointment.icd10Codes field + billing auto-fill fallback | Appointment entity, DTO, seed data, billing |
| H12 | ✅ Empty-state messages ("No X found") | users, roles, referrals, priorAuths |

## 🟠 MEDIUM — Clinical & Workflow Quality

| # | Issue | Location |
|---|-------|----------|
| M1 | ✅ Fee schedule centralized in utils/labels.ts | `billing/index.tsx`, `charges/index.tsx` |
| M2 | ✅ Refills max=0 for controlled, max=11 otherwise | prescriptions |
| M3 | ✅ Prescription date defaults to today | prescriptions |
| M4 | ✅ TERMINAL_APPOINTMENT_STATUSES constant replaces [2,3,4] | appointments |
| M5 | ✅ Lab non-numeric values handled (isNaN guard) | lab views |
| M6 | ✅ Lab flag legend (HH/LL Critical, H/L Abnormal, N Normal) | lab views |
| M7 | ✅ CONSENT_TYPE_LABELS for human-readable display | consent views |
| M8 | ✅ Patient consent revoke button + backend endpoint | patient consent, ConsentController |
| M9 | ✅ getPatientPage size 999→200 across 6 views | referrals, priorAuths, charges, billing, prescriptions, appointments |
| M10 | ✅ parseJwt console.warns on parse failure | `utils/auth.ts` |
| M11 | ✅ Dashboard clinical cards use ?tab= routing + auto-scroll | `dashboard/index.tsx`, `patients/index.tsx` |
| M12 | ✅ PatientLayout fetches name from API, localStorage fallback | `PatientLayout.tsx` |
| M13 | ✅ Claim# column added to billing table | `billing/index.tsx` |
| M14 | ✅ Appointment conflict detection — inline warning in form + saveMutation onError (Round 39) | appointments |
| M15 | ✅ Lab results paginated — server-side page/size + dedicated /trend endpoint (Round 40) | lab views |
| M16 | ✅ Save button disabled + "Checking..." during CDS check | prescriptions |
| M17 | ✅ LOINC filtering server-side — `loinc` param on paginated endpoint (Round 40) | `lab/LabResults.tsx` |
| M18 | ✅ PriorAuth prompt() → useConfirm().prompt() modal | `priorAuths/index.tsx` |

## ⚪ LOW — Technical Debt

| # | Issue | Location |
|---|-------|----------|
| L1 | ✅ Widespread `any` types — no TypeScript safety | global — resolved in Round 43: zero `any` remain; `noImplicitAny: true` now enforced |
| L2 | ✅ `patientInfo` cleared on patient logout | `PatientLayout.tsx` |
| L3 | ✅ Patient tokens cleared on staff logout | `StaffLayout.tsx` |
| L4 | ✅ CarePlan `targetDate` input field added | patients detail |
| L5 | ✅ Immunization `manufacturer`/`site`/`route` inputs added | patients detail |
| L6 | `\|\|` used on potentially-0/false values | multiple |
| L7 | ✅ Charges route registered + sidebar nav item | `App.tsx`, `StaffLayout.tsx` |
| L8 | ✅ Dashboard profile via useQuery (localStorage fallback) | patient/dashboard |
| L9 | ✅ Lab trend arrows `role=img` + aria-label | lab views |
| L10 | ✅ Unused `medicalHistory`/`allergies` removed from emptyForm | patients detail |

## Execution Priority

```
P0 — Security/Compliance (C1-C9)                                    ~1 round
P1 — UX & Data Quality (H1-H12)                                     ~1 round
P2 — Clinical Quality (M1-M18)                                      ~1 round
P3 — Technical Debt (L1-L10)                                        ~1 round
```

## Post-Round 37: Password Reset / Recovery (2026-07-29)

| # | Issue | Plan | Status |
|---|-------|------|--------|
| Gap | No password reset/recovery flow for patients or staff. If user forgets password, they are permanently locked out with no self-service recovery. | Add `POST /api/v1/patient/forgot-password` (sends reset email/token) + `POST /api/v1/patient/reset-password` (token-based reset). Frontend: "Forgot Password?" link on login page → email input → reset token → new password form. Requires email sending infrastructure. For dev mode, show reset token on console. | ✅ Complete (Round 41) |

---

# Round 41: Patient Password Reset ✅ Complete

> **Status: Complete (2026-08-04) — Post-Round 37 gap**

## Goal

Patients can self-service reset a forgotten password. No email infrastructure in scope — dev mode logs the reset token to the server console; production would swap the log for a mailer.

## Changes

| File | Action | Description |
|------|--------|-------------|
| `PatientAuthController.java` | Modify | `POST /api/v1/patient/forgot-password` — 30-min single-use token in an in-memory map (same pattern as SSE tickets); **identical response whether or not the username exists** (no account enumeration). `POST /api/v1/patient/reset-password` — validates+consumes token, BCrypts new password, clears failed attempts/lock, sets `passwordChangedAt`. Both audited (`PATIENT_PASSWORD_RESET_REQUEST` / `PATIENT_PASSWORD_RESET`) |
| `SecurityConfig.java` | Modify | Both endpoints added to permitAll |
| `views/patient/forgotPassword/index.tsx` | **New** | Two-step page: username → reset token + new password + confirm. Raw axios (no auth interceptor — a 401 on an invalid token must not trigger the login redirect) |
| `views/patient/login/index.tsx` | Modify | "Forgot Password?" link |
| `App.tsx` | Modify | `/patient/forgot-password` route |

## End-to-End Verification (H2)

| Check | Result |
|-------|--------|
| forgot-password (patient2) | 200, token in server log ✅ |
| forgot-password (nonexistent user) | 200 — no enumeration ✅ |
| reset-password with token | 200 ✅ |
| login with new password | 200, Maria Garcia ✅ |
| login with old password | 401 ✅ |
| token replay (second use) | 401 single-use ✅ |
| weak password (`123`) | 400 — policy enforced ✅ |

> Note: verification reset patient2's password to `NewPass@123`. Dev DB seeds recreate with `patient123` only on a fresh database.

## Post-Round 37: Dashboard Navigation Improvements (2026-07-28)

| # | Issue | Plan | Status |
|---|-------|------|--------|
| L6 | Dashboard clinical cards (Vital Signs, Problem List, Immunizations, Care Plans) all route to `/patients` list — misleading UX. | Add `?tab=xxx` query param support to patients page. Clicking "Problem List" on dashboard → `/patients?tab=problems`. When clicking a patient row, auto-focus the corresponding tab (problems/vitals/immunizations/care-plans). Requires: update dashboard card paths + patients page URL param handling. | ✅ Complete — dashboard cards use `/patients?tab=problems|immunizations|care-plans`; patients page `useSearchParams` + `scrollIntoView` on `section-${tab}` anchors (vitals/problems/immunizations/care-plans). Landed with Round 37 M11 (`4c9e557`) |

## Stats

- **84 backend endpoints** audited, **70 frontend API functions** mapped, **26 routes** checked
- **21 gaps** found: 8 backend-only, 13 missing clinical features, 5 dead code
- **7 endpoints** intentionally backend-only (interop/external)

---

# Round 38: Chat SSE Token Security — JWT Out of the URL ✅ Complete

> **Status: Complete (2026-08-03) — C5 fix**

## Problem

`useChatSse.ts` opened `new EventSource('/api/v1/chat/subscribe?token=<jwt>')` — the full JWT in the URL query string. URLs land in access logs, proxy logs, and browser history; a leaked JWT grants full API access. `SecurityConfig.sseTokenFilter` existed solely to convert that query param back into a Bearer header.

## Fix: Single-Use Short-Lived Ticket Exchange

EventSource cannot send Authorization headers, so the JWT is exchanged for a random, 30-second, single-use ticket via a normal authenticated HTTP call:

```
Frontend (axios, Bearer header)
  → POST /api/v1/chat/sse-ticket            (JWT validated normally)
  → { ticket: <random 32-hex>, expiresIn: 30 }
Frontend (EventSource)
  → GET /api/v1/chat/subscribe?ticket=...   (permitAll, ticket → userId)
```

- **Ticket**: `UUID.randomUUID()` (122-bit random), bound to userId server-side, 30s TTL, consumed on first use (`TICKETS.remove()`). Replay after use/expiry → 401.
- **Reconnect**: each connection attempt fetches a fresh ticket; ticket fetch failure stops retry (session dead — interceptor redirects to login).
- **JWT never touches a URL.**

## Changes

| File | Action | Description |
|------|--------|-------------|
| `ChatSseController.java` | Modify | `POST /api/v1/chat/sse-ticket` (class-level `@PreAuthorize` moved to this method); `subscribe()` now validates/consumes `?ticket=` instead of SecurityContext principal |
| `SseTicketVO.java` | **New** | `{ ticket, expiresIn }` response DTO |
| `SecurityConfig.java` | Modify | Removed `sseTokenFilter` (query-param→Bearer wrapper); `/api/v1/chat/subscribe` added to permitAll |
| `useChatSse.ts` | Modify | `token` param → `getTicket` callback; fresh ticket per connect/reconnect; ref-stored callback (effect deps `[]`) |
| `api/chat.ts` | Modify | Added `getSseTicket()` |
| `views/chat/index.tsx`, `views/patient/chat/index.tsx` | Modify | Pass ticket fetcher (`request` / `patientRequest`) |

## Security Notes

- Residual risk: a ticket leaked in logs is a 30s window to hijack the user's SSE stream only — no API access, no JWT. Industry-standard pattern for EventSource auth.
- `subscribe` is permitAll by design — the ticket *is* the authentication; identity binding is server-side.
- C4 (EPCS 2FA) remains deferred by design: production environments outsource controlled-substance signing to third-party APIs (Surescripts) with their own 2FA.

## Verified

- Backend `mvn compile` ✅
- Frontend `npx vite build` ✅ (note: `package.json` lost its `build`/`dev` scripts after Round 33 — run `npx vite build` / `npx vite --force` directly)
- No `?token=` or `sseTokenFilter` references remain

## Post-Round 38: Session UX — No More Silent Kicks (2026-08-03)

> **Status: Complete.** Three fixes so users are never "kicked offline" by background activity or idle timeouts.

### 1. Silent 401 for background requests (`request.ts`, `patientRequest.ts`)

**Problem**: the interceptor redirected to login on ANY failed 401 — including background requests like the SSE ticket fetch. An idle user on the chat page got kicked by reconnect activity they never triggered.

**Fix**: per-request `{ silent: true }` flag — refresh is still attempted (so SSE recovers when possible), but the login redirect is skipped for silent requests; the SSE hook just stops quietly. Only user-initiated requests can redirect to login. Applied to `getSseTicket()` (staff) and the patient chat view's ticket call.

### 2. Idle session warning dialog (`useIdleTimeout.ts`, `layout/SessionWarningModal.tsx`)

**Problem**: 30-min idle timeout logged users out silently — no warning, no way to extend.

**Fix**: healthcare-standard warning flow — warning dialog at **25 min** ("Session will expire in 5 minutes"), logout at **30 min**. Any activity (mousemove/keydown/click/touch/scroll) or the **Continue Session** button resets both timers (sliding session). `SESSION_WARNING_MINUTES` / `SESSION_TIMEOUT_MINUTES` in `utils/labels.ts`. Wired into `StaffLayout` + `PatientLayout`.

### 3. Proactive token refresh at 80% TTL (`request.ts`, `patientRequest.ts`, `utils/auth.ts`)

**Problem**: refresh was reactive — only on 401. Users could hit a failed request before the silent refresh kicked in.

**Fix**: `scheduleProactiveRefresh()` schedules a timer at **80% of the access-token TTL** (`scheduleDelayMs()` in `utils/auth.ts`), refreshes via the existing endpoints, and reschedules. Called on login (both pages) and after every successful interceptor refresh. Tokens re-read at fire time to survive rotation; failures fall back to the existing 401 chain.

### Files
`request.ts`, `patientRequest.ts`, `utils/auth.ts`, `utils/labels.ts`, `utils/useIdleTimeout.ts`, `layout/SessionWarningModal.tsx` (new), `layout/StaffLayout.tsx`, `views/patient/layout/PatientLayout.tsx`, `views/login/index.tsx`, `views/patient/login/index.tsx`, `api/chat.ts`, `views/patient/chat/index.tsx`

### Verified
- `npx vite build` ✅
- Known trade-off: multi-tab refresh rotation can invalidate a sibling tab's refresh token (pre-existing with rotation; idle 30-min + 2h access TTL makes collisions rare)

---

# Round 39: Appointment Conflict UI (M14) ✅ Complete

> **Status: Complete (2026-08-03) — M14 fix**

## Problem

Backend rejected conflicting appointments (409) but the appointments form had no conflict display — and `saveMutation` had no `onError`, so the 409 message was invisible to users.

## Changes

| File | Action | Description |
|------|--------|-------------|
| `AppointmentController.java` | Modify | New `GET /api/v1/appointments/conflicts?doctorId=&time=&excludeId=` (ADMIN,DOCTOR) — literal route wins over `/{id}` |
| `AppointmentService.java` | Modify | `findConflicts()` public method reusing `findConflicting` (excludes cancelled, filters `excludeId`); `checkConflict` refactored to reuse it |
| `api/appointment.ts` | Modify | `getAppointmentConflicts(params)` |
| `appointments/index.tsx` | Modify | `useQuery(['appointments','conflicts',...])` enabled when doctor+time set; inline red warning listing conflicting appointments (time + patient); Save disabled while conflicts exist; `saveMutation.onError` displays server 409 message |

## Flow

```
User picks doctor + time in form
  → GET /appointments/conflicts (React Query, keyed by doctor/time/editId)
  → conflicts shown inline: "Doctor already has N appointment(s) within 30 minutes: 2026-08-03T14:30 (Name)"
  → Save disabled while conflicts exist (backend would 409 anyway)
  → any other submit failure now surfaces via saveMutation onError
```

## Verified

- `mvn compile` ✅, `npx vite build` ✅
- React Query v5 resets data on key change — no stale conflict display

---

# Round 40: Lab Results Pagination (M15) + Server-Side LOINC Filter (M17) ✅ Complete

> **Status: Complete (2026-08-03) — M15 + M17 fix**

## Problem

`GET /patients/{patientId}/observations` returned the patient's **entire lab history in one response** — loaded into memory and rendered client-side. LOINC filtering was also client-side (`allList.filter(...)`). At scale this means unbounded payloads and browser-side computation.

## Changes

| File | Action | Description |
|------|--------|-------------|
| `ObservationRepository.java` | Modify | Page-returning variants of both finder methods |
| `LabAnalysisService.java` | Modify | New `pageObservations()` (PageResult, loinc filter applied in SQL); `getTrend()` now requires loinc (blank → empty) |
| `LabResultController.java` | Modify | `GET /patients/{patientId}/observations` → `Result<PageResult<Observation>>` (`?loinc=&page=&size=`, default size 20); new `GET /patients/{patientId}/observations/trend?loinc=` returning full history of one test; `/loinc/catalog` opened to PATIENT (public reference data, needed for the filter dropdown) |
| `PatientPortalController.java` | Modify | Same pagination + `/patient/me/observations/trend` mirror |
| `api/observation.ts` | Modify | `getObservations(patientId, params)`; new `getObservationTrend()` |
| `lab/LabResults.tsx` | Modify | Table mode: server-side pagination (Prev/Next/Total, size 20); trend mode: `/trend` full history drives both the trend panel and table; filter dropdown sourced from LOINC catalog (server filters) |
| `views/patient/lab/index.tsx` | Modify | Same pattern via `patientRequest` |

## Design

```
No test selected  → GET /observations?page=&size=20   → paginated table (+ Prev/Next)
Test selected     → GET /observations/trend?loinc=X   → full history of X → trend panel + table
```

The trend deliberately uses a dedicated endpoint rather than the paginated one: a trend needs the full history of a single test, which is bounded by definition and not a pagination problem.

## Verified

- `mvn compile` ✅, `npx vite build` ✅
- M17 closed as a side effect: LOINC filtering is now server-side (SQL), not client-side

### Infrastructure (2026-08-03): `.nvmrc` for medical-web

Shell default Node is v16.20.2 (`~/.nvm/alias/default` = 16), which cannot run Vite 5 (requires 18+). Added `medical-web/.nvmrc` (`23`) so `nvm use` resolves to v23.0.0; builds must run with that Node. Non-invasive — existing projects using the global default are unaffected.

### Runtime Verification (2026-08-03, H2)

Added patient 101 lab dataset (4 dates × 7 tests = 28 results, HbA1c 7.8→7.5→7.2→6.9) to `DataInitializer` and rebuilt the stale H2 file DB (`CREATE TABLE IF NOT EXISTS` never added the Round 37 H11 `icd10_codes` column to the 7/30 file DB — application failed to boot with `Column A1_0.ICD10_CODES not found`; old DB backed up to `/tmp/backup_medical_dev_0803.mv.db`).

| Check | Result |
|-------|--------|
| `GET /patients/101/observations?page=1&size=20` | total=28, 20 records, dates sorted desc ✅ |
| `page=2` | 8 records ✅ |
| `?loinc=4548-4` (server-side filter) | total=4, all HbA1c ✅ |
| `GET /patients/101/observations/trend?loinc=4548-4` | 4 records, 7.8→6.9 (downward) ✅ |
| `GET /patients/100/observations` (30 rows) | total=30, 20/page ✅ |
| Patient portal `me/observations` + `me/observations/trend` | pagination + trend work (patient2/p101) ✅ |

Note: integration endpoints (`/integration/*`) require **both** `Authorization: Bearer` (ADMIN/DOCTOR) and `X-Integration-Key` — the class-level `@PreAuthorize` on `IntegrationController` predates the API-key check; not part of this round.

---

# Post-Round 42: Full-System Review (2026-08-04)

> **Status: Audit complete — VERDICT: Ready to merge. 0 CRITICAL, 0 HIGH, 2 MEDIUM, 2 LOW.**
> **Method: checklist review of all 29 controllers + key services/entities + frontend falsy safety + cross-cutting**

## Findings

### 🟠 MEDIUM

| # | Issue | Location | Note |
|---|-------|----------|------|
| R-1 | Raw entity responses (violates "never raw entities" + no @PhiField masking) | `ReferralController`, `RefillController`, `ProblemController`, `ImmunizationController`, `CarePlanController`, `PriorAuthController` — create/update/approve/deny return `Result<Entity>` | Low real risk (authenticated users only; storage-layer encryption intact) but inconsistent with the VO pattern used everywhere else |
| R-2 | Missing `@Valid` on create/update request bodies | Referral / Immunization / Problem / CarePlan / PriorAuth controllers | No impact today — the inline Form DTOs carry no validation constraints — but constraints added later would silently not apply |

### ⚪ LOW

| # | Issue | Location |
|---|-------|----------|
| R-3 | Silent catch on catalog load failure — blank page with no error | `views/lab/LoincCatalog.tsx` |
| R-4 | Silent catch on RxNorm auto-lookup failure (manual entry fallback exists) | `views/prescriptions/index.tsx` |

### ✅ Verified Clean

- `@PreAuthorize` on every endpoint except auth endpoints (login/refresh/logout — by design)
- `@Valid` on core-module request bodies; all Round 38-41 endpoints validated
- `@Transactional`/`@Auditable` coverage on mutations
- Frontend falsy safety: zero `|| null` / `|| 0` patterns
- No hardcoded credentials, no new dependencies, no cyclic references
- Raw axios only in the emergency-access (break-glass) flow — intentional, uses the short-lived `emToken`

## Resolution Status

R-1/R-2: ✅ Fixed in Round 44 (2026-08-07). R-3/R-4: ✅ Fixed in Round 44 follow-up (2026-08-10). All Post-Round 42 findings closed.

---

# Round 43: Eliminate All TypeScript `any` Types (Round 37 L1) ✅ Complete

> **Status: Complete (2026-08-07) — 193 explicit `any` sites eliminated, zero remain. `npx tsc --noEmit` passes with `noImplicitAny: true` (previously the build never ran tsc).**
> **Method: Plan → Implement done directly. ~57 files changed.**

## Problem

193 explicit `any` types across the frontend (56 in `api/` + `utils/` + `layout/`, ~123 in `views/`, plus 8 `as any` casts, 3 `<any>`, 1 `as any[]`). The axios interceptors unwrap `Result<T>` at runtime but statically `request.get()` returned `Promise<AxiosResponse<any>>` — `.records`/`.total` accesses compiled only by accident. `npm run build` never ran `tsc` (`"build": "vite build"`), and `tsconfig.json` had `noImplicitAny: false` — the `any` forest never surfaced.

## Changes

### New: Typing infrastructure
| File | Description |
|------|-------------|
| `src/types/common.ts` | `Result<T>` / `PageResult<T>` (`total, size, current, records`) / `PageQuery` / `JwtPayload` / `IdName` |
| `src/types/entities.ts` | ~50 interfaces — one per backend VO (numeric ids) + per-module form types (string inputs, sent directly as create/update payloads) + create payload types (converted numbers) + `LoginResponse` / `PatientLoginResponse` |
| `src/vite-env.d.ts` | **New (was missing)** — Vite CSS-module declarations; pre-existing tsc failures surfaced by the new type gate |

### API layer (`api/*.ts`, 27 files + `utils/auth.ts`)
- **Typed `http` facade** in `request.ts` / `patientRequest.ts`: `http.get<T>()` etc. resolve to the unwrapped payload type (matching the interceptor's runtime unwrap) instead of `AxiosResponse<T>`.
- **Axios module augmentation**: `_retry?: boolean; silent?: boolean` on `AxiosRequestConfig` — removes the `{ silent: true } as any` casts.
- Typed interceptors (`InternalAxiosRequestConfig`, `AxiosResponse<Result<unknown> | Blob>`, `AxiosError<{ message?: string }>`); early-return guard when `err.config` is undefined; refresh calls typed via `axios.post<Result<LoginResponse>>`.
- `params: any` → per-module query interfaces; `data: any` → form/create-payload interfaces; every function returns a typed `Promise<T>`.
- `parseJwt()` returns `JwtPayload`; `csv(v: unknown)`; refill/charge/bill/priorAuth/referral update signatures accept partial/status payloads.

### Views (~35 files)
- `emptyForm: any` → typed form interfaces; `useState<any[]>` → typed entity arrays; mutationFn/`onError`/map callbacks lose `any` annotations (inferred from the typed API layer); `catch (err: any)` → `catch (err: unknown)` + `instanceof` narrowing; `openForm(row?: any)` → `row?: <Entity>VO`.
- Dynamic-key `setForm({ ...form, [f]: value })` sites → `as const` field arrays + cast at the setForm boundary.
- `as any` sites fixed individually: chat JWT payloads via `parseJwt`, SSE ticket via typed `SseTicketVO`, billing/charges union fallback via `Promise.resolve<PageResult<AppointmentVO>>`, dashboard `keyof DashboardStats`, ConfirmDialog `null`, StaffLayout divider union type, prescriptions patient lookup.
- Chat views + `useChatSse` share `MessageVO`/`ConversationVO` from `types/entities` (were duplicated per file).
- Patient portal views migrate from raw `patientRequest.get(...)` to the typed `http` facade.

### Config
- `tsconfig.json`: `noImplicitAny: false` → `true`.

## Verified

- `npx tsc --noEmit` — **zero errors** (first real type gate; previously never ran)
- `grep -rnE '\bany\b|<any>' src` — zero hits
- `npm run build` — passes (213 modules, 430KB JS)
- Runtime smoke (H2 backend + API): staff login → patients/appointments/bills/prescriptions/charges/referrals lists 200; patient login → all 13 portal endpoints 200; create appointment + bill 200; CDS check `passed=true`; `npx vite build` of dev proxy OK
- Two latent type-level bugs surfaced and fixed by the gate: missing `vite-env.d.ts` CSS declarations, and `useRef<ReturnType<typeof setTimeout>>()` missing initial argument

## Notes

- Zero runtime behavior changes by design (typing only). Two benign hardening touches: interceptor early-return on `err.config === undefined`, and login token persistence falls back to `''` instead of stringifying `undefined`.
- `mvn` backend was started for the smoke test and left running (H2 profile) — stop with `lsof -ti:8080 | xargs kill` if not needed.

---

# Round 44: R-1/R-2 — VO Returns + @Valid on Remaining Controllers ✅ Complete

> **Status: Complete (2026-08-07) — Post-Round 42 findings R-1 (raw entity responses) and R-2 (missing @Valid) resolved.**

## Changes

### New VOs (6) — `fromEntity()` in the DTO class per project convention
| VO | Module |
|----|--------|
| `ReferralVO` | appointment/dto |
| `RefillRequestVO` | prescription/dto |
| `ProblemVO` / `ImmunizationVO` / `CarePlanVO` | patient/dto |
| `PriorAuthVO` | billing/dto |

Each VO mirrors the entity fields exactly — **field names verified identical to the frontend types via live API smoke** (JSON key sets compared on all 6 list endpoints + creates).

### Controllers (6) — no raw entities returned anywhere
- All `list` endpoints → `Result<PageResult<VO>>`; all create/update/approve/deny → `Result<VO>`; `ReferralController.listByPatient` and `RefillController.listMine/listPending` → `Result<List<VO>>`.
- `@Valid` added to all create/update request bodies (9 sites across Referral/Problem/Immunization/CarePlan/PriorAuth; Refill already had it).
- `@NotNull` on `patientId` in ReferralForm + PriorAuthForm — missing patientId now returns 400 with a clear message instead of 500 (DB constraint).

### Bonus fix (discovered during verification)
- **Referral create was broken**: `referring_doctor_id` is NOT NULL but the frontend form never sends `referringDoctorId` → every UI-created referral 500'd. Now defaults to the authenticated user (the doctor creating the referral).

## Verified

- `mvn test`: 135 tests, 0 failures (H2 in-memory)
- Live API smoke (H2 backend): all 6 list endpoints return VO JSON with unchanged field names; create problem/immunization/care-plan/referral/prior-auth all 200; missing `patientId` → 400 `"patientId: must not be null"`; referral create now persists with `referringDoctorId` = login user
- Frontend unaffected: response field names identical to before (verified against Round 43 `src/types/entities.ts`)

### Round 44 follow-up: R-3/R-4 — Silent Catches (2026-08-10) ✅

| Finding | Fix |
|---------|-----|
| R-3: LoincCatalog load failure → blank page | `loadError` state + error banner with Retry button (re-runs the load) |
| R-4: RxNorm auto-lookup silent failure | `rxLookupError` state, non-blocking inline hint in the items section. Covers both failure (network/5xx → "RxNorm lookup failed") and no-match (unknown code → "No drug found for RxNorm X — enter the drug name manually"); cleared on next input or success |

Verified: `npx tsc --noEmit` clean (noImplicitAny: true), `npm run build` passes. Backend RxNorm lookup confirmed: valid code → drugName filled; unknown code → 200 + empty drugName (now surfaced as a hint instead of silence).

---

# Post-Round 44: Full-System Review II (2026-08-12)

> **Status: Audit complete — VERDICT: Blocked. 1 CRITICAL, 2 HIGH, 3 MEDIUM, 3 LOW.**
> **Method: checklist review of all 38 controllers + 32 entities + 19 services + full frontend views + config/deps. Follow-up to the Post-Round 42 review.**

## Findings

### 🔴 CRITICAL

| # | Issue | Location | Note |
|---|-------|----------|------|
| R2-1 | Chat ID-space collision: `Message.sender_id/receiver_id` mix patient table IDs and sys_user IDs in one Long space (both start at 1 in seed data). Patient with id N reads staff user N's messages to **other** patients (`findRecentMessagesByUser`), SSE emitters keyed by bare userId overwrite each other (push misdelivery), `resolveName` checks patient table first (wrong names), `JwtClaimMapper` force-logout check hits sys_user for patient tokens | `chat/entity/Message`, `ChatService`, `ChatSseController`, `JwtClaimMapper`, `PatientChatController` | Fix direction: senderType/receiverType on Message (or `staff:`/`patient:` key prefixes in EMITTERS), `resolveName` by type, skip force-logout check for PATIENT-role tokens |

### 🟡 HIGH

| # | Issue | Location | Note |
|---|-------|----------|------|
| R2-2 | DOCTOR patient scoping claimed in API-LAYOUT for appointments/prescriptions/bills lists but only `BillService.resolveDoctorScope()` implements it — `AppointmentService.page` and `PrescriptionService.page` do bare `findAll`. Vitals/labs/charges unscoped (docs don't claim those). Decision needed: implement scoping everywhere or fix docs | `appointment/service/AppointmentService`, `prescription/service/PrescriptionService`, `docs/API-LAYOUT.md` | |
| R2-3 | `ChargeController.create` accepts `@RequestBody ChargeForm` with no `@Valid` and zero constraints (null `patientId`, negative `chargeAmount` persist). Manual entity mapping + `@Transactional`/`@Auditable` live in the controller instead of a service; returns raw `Result<Charge>`/`Result<Bill>` | `billing/controller/ChargeController` | |

### 🟠 MEDIUM

| # | Issue | Location | Note |
|---|-------|----------|------|
| R2-4 | Integration writes lack `@Auditable`: `AdtService.processAdt` (Patient upsert) and `LabResultService.processLabResults` (Observation bulk insert) — Mirth-sourced PHI writes leave no audit trail (21 CFR Part 11) | `integration/service/AdtService`, `integration/service/LabResultService` | Both already `@Transactional` |
| R2-5 | Raw entity responses remain after R-1: `VitalSignController` (list+create), Observation via `LabResultController`/`PatientPortalController`/`LabAnalysisService`, `ChargeController` (Charge/Bill). Bill carries an AES field decrypted on read; entity internals leak into API contract | 5 sites | R-1 fixed 6 controllers in Round 44; these were missed |
| R2-6 | Free-text clinical fields plaintext while `Message.content` is encrypted: `Appointment.chief_complaint/description/notes`, `Charge.notes`, `Referral.notes`, `VitalSign` | 4 entities | PHI-at-rest scope inconsistent; free text can embed identifiers |

### ⚪ LOW

| # | Issue | Location | Note |
|---|-------|----------|------|
| R2-7 | `data/` (H2 file DB at repo root) not in .gitignore — untracked in git status | `.gitignore` | |
| R2-8 | `/patient/forgot-password`, `/patient/reset-password` not rate-limited (only login/refresh/export have filters); reset token logged (documented dev-only) | `PatientAuthController`, `RateLimiterConfig` | |
| R2-9 | `SecurityConfig` permits `/h2-console/**` unconditionally (console enabled only in h2 profile — the default active profile — empty password) | `common/config/SecurityConfig` | Dev-only exposure; gate the matcher on the profile |

### ✅ Verified Clean

- `@PreAuthorize` on every endpoint (class-level annotations cover apparently-missing ones); auth endpoints (login/refresh/forgot/reset) permitAll by design
- `@Valid` on request bodies except the R2-3 site; Refill deny `@RequestBody(required=false)` null-guarded
- Patient auth: BCrypt, failed-attempt lockout (15 min), password history (last 3), single-use 30-min reset tokens, refresh-token scope check
- SSE ticket flow: single-use, 30s TTL, JWT never in subscribe URL
- All user-facing CUD ops `@Transactional` + `@Auditable` (ChatService uses the fully-qualified annotation)
- No raw SQL concatenation, no new dependencies (pom matches mandated stack), no hardcoded prod credentials (all `${ENV}`), `dev-mode` requires explicit `true`
- Frontend: zero `|| null`/`|| 0` on numerics (`|| null` sites are string fields), zero `.catch(() => {})`, no `Number(x) ||` patterns; raw axios only in break-glass flow + pre-auth patient pages (acceptable)

## Resolution Status

- R2-1: ✅ Fixed in Round 45 (2026-08-12). R2-2: ✅ Fixed in Round 47 (2026-08-12). R2-3..R2-9: ✅ Fixed in Round 48 (2026-08-13). All Post-Round 44 findings closed.

---

# Round 45: R2-1 — Chat ID-Space Collision Fix ✅ Complete

> **Status: Complete (2026-08-12) — Post-Round 44 finding R2-1 (CRITICAL chat ID-space collision) resolved.**

## Problem

`Message.sender_id`/`receiver_id` stored bare Longs mixing two independent ID spaces (sys_user and patient, both starting at 1). A patient whose id equals a staff user's id could read that staff member's messages to **other** patients via `findRecentMessagesByUser`; SSE emitters keyed by bare userId overwrote each other (push misdelivery); `resolveName` checked the patient table first (wrong names); `JwtClaimMapper` force-logout check hit sys_user for patient tokens (wrong token revocation).

## Changes

### Backend
| File | Change |
|------|--------|
| `chat/entity/Message` | New `sender_type`/`receiver_type` VARCHAR(10) columns (`STAFF`/`PATIENT`) |
| `chat/repository/MessageRepository` | All queries type-guarded: `findMessagesBetween`, `findRecentMessagesByUser` (now JPQL + Pageable), `countUnread`, `markAsRead`; removed unused `findAllMessagesByUser` |
| `chat/service/ChatService` | Typed signatures (`senderType`/`receiverType`/`partnerType`); conversation grouping by `(type, id)` composite; `resolveName(type, id)`; `STAFF`/`PATIENT` constants |
| `chat/dto/MessageVO` / `ConversationVO` | VO carries `senderType`/`receiverType` / `partnerType` |
| `chat/dto/MessageFormDTO` | Optional `receiverType` (staff sends require it — controller validates; patient portal omits it, controller defaults `STAFF`) |
| `chat/controller/ChatController` | `GET /messages/{partnerId}` requires `partnerType` param (invalid → 400); `POST /messages` requires `receiverType` (missing/invalid → 400) |
| `chat/controller/PatientChatController` | Literal types: sender `PATIENT`, partner/receiver always `STAFF` — patient portal API unchanged |
| `chat/event/NewMessageEvent` + `ChatEventListener` | Event carries both types; push routes by typed key |
| `chat/controller/ChatSseController` | `EMITTERS` keyed `type:id`; `SseTicket` carries type from authorities (ROLE_PATIENT) |
| `security/JwtClaimMapper` | Force-logout check skipped for PATIENT-role tokens (groups read before the check) |
| `sql/schema.sql` | `message` table gains `sender_type`/`receiver_type` NOT NULL |
| `common/config/DataInitializer` | `seedMessages` inserts typed rows (patients 100/101 ↔ staff 2) |

### Frontend
| File | Change |
|------|--------|
| `types/entities.ts` | `MessageVO` + `ConversationVO` gain `'STAFF' \| 'PATIENT'` type fields |
| `api/chat.ts` | `getMessages(partnerId, partnerType, ...)`, `sendMessage(receiverId, receiverType, content)` |
| `views/chat/index.tsx` | Partner carries type; conversation keys `type:id`; optimistic messages typed; `isMe` + SSE filter check both type and id; URL param `partnerType` |
| `views/patient/chat/index.tsx` | Optimistic messages typed (`PATIENT`→`STAFF`); `isMe`/SSE filter type-aware; API calls unchanged |
| `views/patients/index.tsx` | "Msg" button link adds `&partnerType=PATIENT` |

### Tests
- `IntegrationTest`: `getConversation` gains `partnerType=PATIENT`; `sendMessage` gains `receiverType`; new negative test `sendMessage_withoutReceiverType_shouldReject` (HTTP 400); new collision regression test `patientChat_idSpaceCollision_shouldNotLeakStaffMessages` — inserts a message "from staff 100 to patient 99" and asserts patient 100's conversation list shows only partner `STAFF:2`.

## Verified

- `mvn test`: **137 tests, 0 failures** (was 135 — 2 new tests)
- `npx tsc --noEmit`: clean
- Live smoke (H2 backend, fresh schema + seed):
  - doctor send with `receiverType=PATIENT` → 200, message typed `STAFF→PATIENT`; without → 400 `"receiverType is required"`
  - invalid `partnerType=DOCTOR` → 400 `"Invalid receiverType: DOCTOR"`
  - doctor conversations → `PATIENT 100 James Anderson`, `PATIENT 101 Maria Garcia` (typed, correct names)
  - patient conversations → single partner `STAFF 2 Dr. Sarah Mitchell` (was previously resolved patient-first — wrong name on collision)
  - patient portal send → 200 typed `PATIENT→STAFF` (no body change)

## Notes

- Dev H2 file DB wiped and regenerated (schema change; seed-only data). Both `data/` copies still untracked — R2-7 (.gitignore) remains pending.

---

# Round 46: Chat Unread Badge in Sidebars ✅ Complete

> **Status: Complete (2026-08-12) — sidebar "Messages" item now shows a live unread count badge (staff + patient portal).**

## Problem

New messages only became visible after opening the chat page — the sidebar gave no notification.

## Changes

### Backend
| File | Change |
|------|--------|
| `chat/repository/MessageRepository` | New typed `countUnreadByUser(userId, type)` |
| `chat/service/ChatService` | New `unreadCount(userId, userType)` |
| `chat/controller/ChatController` | `GET /messages/unread-count` → `Result<Integer>` (ADMIN/DOCTOR) |
| `chat/controller/PatientChatController` | `GET /patient/me/messages/unread-count` → `Result<Integer>` (PATIENT) |

### Frontend
| File | Change |
|------|--------|
| `api/chat.ts` | `getUnreadCount()` → `/messages/unread-count` |
| `layout/StaffLayout.tsx` | Red badge on Messages item; fetch on route change + poll every 30s; 99+ cap |
| `layout/StaffLayout.module.css` | `.unreadBadge` style |
| `views/patient/layout/PatientLayout.tsx` | Same badge on portal Messages item via `http.get('/patient/me/messages/unread-count')` |

SSE stays page-local (chat view owns the single emitter per user) — the sidebar deliberately polls instead of opening a second SSE connection that would evict the chat page's emitter.

## Verified

- `mvn test`: 137 tests, 0 failures
- `npx tsc --noEmit`: clean
- Live smoke: patient1 unread → 0 (conversation was auto-read by prior smoke), doctor unread → 0; patient sends message → doctor unread → 1 (badge data flows end to end)

---

# Round 47: R2-2 — DOCTOR Patient Scoping ✅ Complete

> **Status: Complete (2026-08-12) — Post-Round 44 finding R2-2 (HIGH: DOCTOR scoping claimed in docs but only bills implemented) resolved.**

## Problem

API-LAYOUT claimed "DOCTOR scoped to own patients" for appointments/prescriptions/bills lists, but only `BillService` (and CSV export) implemented it; `AppointmentService.page` and `PrescriptionService.page` returned everything. Detail endpoints (`GET /bills/{id}` etc.) had no ownership check anywhere — scoping a list without detail checks is bypassable by id enumeration. Bonus latent bug found during the fix: the existing ADMIN checks used `LoginUser.getAuthorities()` which mirrors token *scopes*, not roles — the ADMIN bypass never fired.

## Solution

"Own patients" = patients where the doctor has appointments or prescriptions (the definition already used by bills/export).

### Backend
| File | Change |
|------|--------|
| `common/security/DoctorPatientScope` (new) | Shared resolver: `resolve()` → null for ADMIN, else patient-id set (appointments ∪ prescriptions ∪ emergency patientId). `requireAccess(patientId)` → 403 for out-of-scope. Role check via `Authentication` authorities (fixes the latent ADMIN-bypass bug) |
| `billing/service/BillService` | Uses shared resolver; latent empty-scope→null bug fixed (empty now filters to nothing); `getById`/`submitClaim` add 403 |
| `export/controller/ExportController` | Uses shared resolver (removed duplicate `resolveExportScope`) |
| `appointment/service/AppointmentService` | `page` filtered; `getById`/`update` add 403 |
| `prescription/service/PrescriptionService` | `page` filtered; `getById`/`getByPatientId`/`cancel` add 403 |
| `billing/controller/ChargeController` | `list` filtered; `convert` adds 403 |
| `prescription/controller/RefillController` | Pending list filtered (new repo query `findByStatusAndPatientIdInOrderByRequestedAtDesc`); `approve`/`deny` add 403 |
| `patient/controller/VitalSignController`, `LabResultController` (observations + trend), `ProblemController`, `CarePlanController`, `ImmunizationController`, `PatientController` (history/allergies reads + allergy resolve), `PatientCaseController` (FHIR case) | by-patient reads/updates add 403 |

Deliberately unscoped: create endpoints and the patient directory/detail (`GET /patients`, `GET /patients/{id}`) — booking or prescribing for a new patient establishes the care relationship; emergency break-glass tokens bypass via the `patientId` claim.

### Tests
6 new integration tests (Orders 72–77): out-of-scope 403 on vitals + 7 clinical read endpoints; charge list excludes patient 102; appointment detail 403 (with in-scope 200 control); admin sees all; emergency token bypasses scope.

## Verified

- `mvn test`: **143 tests, 0 failures** (was 137 — 6 new)
- Existing 137 tests unaffected (admin-token list tests stay unscoped; doctor tests use patients 100/101, both in doctor 2's scope)
- `npx tsc --noEmit`: clean (no frontend changes)

## Notes

- FHIR read endpoints (`/api/v1/fhir/Patient/{id}` etc.) remain unscoped — machine-facing consumers, flagged as a follow-up decision.

---

# Round 48: R2-3..R2-9 — Validation, Audit, VOs, Free-Text Encryption, Hardening ✅ Complete

> **Status: Complete (2026-08-13) — all remaining Post-Round 44 findings closed (R2-3 HIGH; R2-4/5/6 MEDIUM; R2-7/8/9 LOW).**

## Changes

### R2-3 (HIGH) — ChargeController validation + architecture
- `ChargeForm` moved to `billing/dto/` with constraints (`patientId` @NotNull @Positive — blocks the frontend's `Number('')=0` case; `chargeAmount` @NotNull @PositiveOrZero; `units` @PositiveOrZero) + `toEntity()`
- New `ChargeService` owns `@Transactional`/`@Auditable`/scope filtering; controller is thin and applies `@Valid`
- `convert` now throws `BusinessException(CONFLICT)` (was inline `Result.fail(409)`) and returns `BillVO` (`BillService.toVO` made public; private duplicate removed)
- Raw `Result<Charge>`/`Result<Bill>` replaced with `ChargeVO`/`BillVO`

### R2-4 (MEDIUM) — Integration audit trail
- `@Auditable(module="integration")` on `AdtService.processAdt` (ADT_UPSERT) and `LabResultService.processLabResults` (LAB_RESULTS) — Mirth-sourced PHI writes now leave audit records

### R2-5 (MEDIUM) — Raw entity responses eliminated
- New `VitalSignVO` + `ObservationVO` (patient/dto, `fromEntity` per convention)
- `VitalSignController`, `LabResultController`, `PatientPortalController`, `LabAnalysisService` all return VOs; field names identical to prior entity JSON → zero frontend changes

### R2-6 (MEDIUM) — Free-text clinical encryption
- `@Convert(AesAttributeConverter)` on `Appointment.chiefComplaint/description/notes`, `Charge.notes`, `Referral.notes`, `VitalSign.notes`
- DataInitializer seeds encrypt these columns (raw SQL bypasses the converter)
- Note: Problem/CarePlan/PriorAuth/MedicalHistory free-text fields remain plaintext — flagged as candidates for a follow-up round

### R2-7 (LOW) — .gitignore
- `data/` + `medical-server/data/` added (H2 file DBs no longer show as untracked)

### R2-8 (LOW) — Password reset hardening
- New rate limiter: forgot-password + reset-password, 5/min/IP (`rate:password-reset:`)
- Reset token logged only when `app.security.dev-mode: true` (prod logs username only — tokens never hit prod logs; dev-mode log is the delivery channel until a mailer exists)

### R2-9 (LOW) — H2 console gating
- `SecurityConfig` permits `/h2-console/**` only when the `h2` profile is active; other profiles require auth (401) instead of exposing an empty-password console

### Tests
- `createCharge_missingPatient_should400`, `createCharge_valid_shouldSucceed_andRoundTripEncryptedNotes` (verifies AES round-trip through the converter)

## Verified

- `mvn test`: **145 tests, 0 failures** (was 143 — 2 new)
- `npx tsc --noEmit`: clean
