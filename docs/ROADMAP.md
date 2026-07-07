# Project Evolution Roadmap — All Complete ✅

> From the HIPAA + FHIR + US-Model three-pillar foundation, through clinical decision support, lab interoperability, ePrescribing, compliance audit remediation, and frontend migration.
>
> **Status: 9 Rounds + 3 Compliance Rounds + Frontend Migration + RBAC Remediation + Doctor-Patient Messaging — All Complete (2026-07-06)**

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

## Next Round
Round 21: CDS frontend integration — first Workflow-driven round using the agent configs.
