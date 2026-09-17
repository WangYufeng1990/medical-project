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
>
> **Full-System Review III (2026-08-20): independent external code review (backend 207 Java files + frontend 87 TS/TSX + all config). Verdict: Blocked — 8 CRITICAL, 24 HIGH, 16 MEDIUM, 12 LOW identified, NOT fixed (review only, no code changes per user instruction). See Full-System Review III section at the end.**
>
> **Fix progress (2026-08-20): Fix batches 1–6 + C2 key-rotation fix ALL COMPLETE — all 8 CRITICAL + 24 HIGH findings closed (audit credentials, deployment security, access control, prescriptions/CDS, data integrity, hardening, key rotation). 162 tests + tsc + prod build green.**
>
> **Post-review ops fix (2026-08-20): h2 file DB anchored to `${user.home}/.medical-dev/data/medical_dev` (was `./data/medical_dev`, CWD-relative — running from project root vs `medical-server/` silently opened two different DBs; the stale file also lacked `audit_log.prev_hash`, so Review III chain-hash writes failed, and old SQL-eCQM zero results persisted). `H2_DB_PATH` env overrides. Stale `data/` files removed; schema + seed rebuild on next h2 boot.**
>
> **Round 50 M6 ✅ complete (2026-09-15) — crypto & error-handling contract (F6, F10).** `encrypt()` now **refuses the write** instead of returning null, so a broken key can no longer turn into silently empty clinical fields (proved by a mutation A/B: 23 absorbed failures before, a refused write after); the catch-all `IllegalArgumentException`→400 is gone and client mistakes are mapped honestly (**404** for an unknown path and **405** for a wrong verb, both of which answered 500 before); all seven `catch (Exception ignored)` are gone; and the restart guard now compares a **value** from a dedicated `KEY_ROT_FINGERPRINT` audit row instead of parsing prose. Verifying that last point exposed two more bugs — a 24-char event name overflowing `VARCHAR(20)`, and a bean-init ordering bug that meant the startup audit and the mismatch check **had never run at all** (`key_audit` held no `KEY_INIT` row in any boot). **166 tests green.**
>
> **Round 50 M9 ✅ complete (2026-09-15) — tooling guardrails, dead code, doc sync (F12).** The CLAUDE.md review checklist is now executable: **ESLint** (`npm run check` = lint + `tsc --noEmit`, clean with 4 deliberate warnings) and **maven-enforcer** on `verify` (Maven/Java versions, duplicate POM versions, dependency convergence). The convergence rule immediately paid for itself by exposing a hidden mixed-version HAPI tree (`hapi-fhir-base` 7.4.0 vs 6.4.1) — now pinned so the tree is honest. Dead code gone (`CsvUtil`, the unused `hutool` dependency, a stray empty package), logout key lists centralised in `tokenStore`, and the doc drift fixed (API-LAYOUT's bill-pay role, CLAUDE.md's springdoc/Hutool rows). **166 tests green via `mvn clean verify`.** Also fixed the reason `npm run lint` failed on a Node 16 shell (`structuredClone is not defined`) — `.nvmrc` pointed at an uninstalled Node 23, now 22 with `engines.node >=20.9` declared.
>
> **Round 50 M7 ✅ complete (2026-09-14) — pagination unification + input bounds (F8, F11).** One `Pages.of(...)` builds every user-facing pageable and owns the 200-row cap: `?size=9999` (which the frontend really used) is now **400 instead of 200**, `page=0` is 400 instead of 500, and the 19 raw-`@RequestParam` endpoints that had *no* bound are covered by the same rule. `@Size` bounds derived from the AES storage rule (`VARCHAR(n)` fits `n/2 - 29` plaintext chars) turn an over-long `medicalHistory` into a 400 instead of a database error — verified exact at the boundary (1 971 accepted, 3 000 rejected). Also fixed FHIR `_count=0` dividing by zero. **166 tests green, tsc clean.** **\n>\n> **Round 50 M5 ✅ complete (2026-09-14) — domain status enum + mapping fixes (F4).** `AppointmentStatus` (SCHEDULED/ARRIVED/CANCELLED/COMPLETED/NO_SHOW) replaces every magic appointment integer including the JPQL `status <> 2` (now a parameter); the DB column and wire format stay numeric. Four confirmed defects fixed and verified live against seeded data: a **no-show visit no longer exports as a cancelled Encounter** (it emits none), "Not Hispanic or Latino" no longer returns the **Hispanic** OMB code, preferred language moved from a mislabelled `us-core-birthsex` extension to `Patient.communication.language`, and the codebase's only bare `orElseThrow()` now returns 404 instead of 500. **166 tests green.** **\n>\n> **Round 50 M4 ✅ complete (2026-09-14) — test-suite decomposability (F3).** The 2,109-line / 128-test `IntegrationTest` monolith is now 17 classes along its own section seams plus `IntegrationTestSupport`; suite-wide static tokens are gone (each class logs in its own `@BeforeAll`), cleanup runs per class, and **166 tests pass in both class orders** (17 s, one shared context) with single classes and single methods runnable alone. The split immediately exposed a real defect the old single class had been masking: `AesAttributeConverterTest` swapped the **process-wide AES key** and never restored it, so every integration class running after it in the same JVM 500'd on save (seeded rows decrypted to `[DECRYPT_FAILED]`) — fixed with a snapshot/restore seam. **\n>\n> **Round 50 M10 ✅ complete (2026-09-14) — rate limiter config actually applies (F15).** A configured limit now takes effect: the effective limit is part of the limiter key (`rate:export:100:3600:<ip>`), because Redisson's `trySetRate` only initialises a limiter that does not exist and the leftover permit counters had no TTL — an instance set to `export-per-hour=3` was measured serving **21 consecutive exports** as 200. Verified by changing the limit on the same Redis with no cleanup (`200×5` then `429`, message now reading "Max 5" from config), all four limiters still enforcing, `mvn test` 166 green. The four duplicated inline filters collapsed into one factory (130 → 99 lines). Also corrected the README cleanup pattern to `*rate:*` — Redisson keeps three keys per limiter and matching only the first leaves the consumed budget behind. **
>
> **Round 50 M3 ✅ complete (2026-09-11) — API client consolidation + auth contract.** `api/createClient.ts` now holds the single implementation of token injection, single-flight refresh, proactive refresh, the blob branch and error mapping; `request`/`patientRequest` are 14/12-line instances, so all 45 importing files were untouched. The refresh-failure **hang is fixed** (parked requests are now rejected instead of silently dropped), the refresh reads only `token` and fails loudly when it is missing, `LoginResponse` no longer invents `accessToken`/`expiresIn`/`user`, and the proactive timer got a 5 s floor that removes a 0 ms refresh loop. Verified by executing the real module against a fake 401/refresh server (5 cases) + tsc/build. `tsc`/`build` clean, bundle 435.28 → 433.41 kB. **
>
> **Round 50 M2 ✅ complete (2026-09-11) — export path unification.** The sidebar CSV export now calls the backend streaming endpoints (`/api/v1/export/patients|bills`) instead of rebuilding the CSV in the browser: PHI masking, the formula-injection guard, the export rate limit and the `EXPORT_*` audit rows all apply to what the user actually clicks. Blob downloads resolve to `{ blob, filename }` and a JSON error body wrapped in a Blob is now decoded, so a 429 reaches the user with the backend's message. `tsc` + `vite build` clean; backend contract, audit rows, parser and the 429 path verified. Found F15 (stale Redis limiter state silently pins an old limit) → new batch M10. **
>
> **Round 50 M1 ✅ complete (2026-09-11) — h2 quick-start correctness.** The documented h2 quick start (`SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run`) now really is dependency-free: `app.rate-limit.enabled: false` alone was **not** enough (Redisson's auto-config builds its client eagerly, so boot still died at `redisTemplate → redissonConnectionFactory → redisson`) — `spring.autoconfigure.exclude: org.redisson.spring.starter.RedissonAutoConfigurationV2` in `application-h2.yml` is what fixes it. New `DevSchemaGuard` (+ `schema_version` table) turns silent `schema.sql` drift into a loud startup failure; README documents prerequisites, the reset procedure, `H2_DB_PATH` and how to re-enable rate limiting. **166 tests, 0 failures** (162 prior + 4 new). Remaining: M2–M9. See the Round 50 section at the end.**
>
> **Maintainability review (2026-09-11): independent code-quality review (backend 212 main + 7 test Java files; frontend 81 TS/TSX + 6 CSS; schema, pom and all config) → 15 findings (4 🟡 HIGH, 10 🟠 MEDIUM, 1 ⚪ LOW), tracked as Round 50: Maintainability Pass — 9 of 10 batches done (M1–M7, M9, M10; M8 in progress — 3 of its 6 slices landed) — F15 closed by M10, F3 by M4, F4 by M5, F6+F10 by M6, F8+F11 by M7, F12 by M9. Scope decision: H2-only learning demo ⇒ DB migration tooling out of scope (finding withdrawn; only H2-file hygiene kept as F14/M1). Headline finding, verified at boot: the documented h2 quick start could not boot without Redis (README claimed "no external dependencies") — fixed in M1. See the Round 50 section at the end.**

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

---

# Round 49: Free-Text Encryption Completion + FHIR Read Scoping ✅ Complete

> **Status: Complete (2026-08-17) — Items 1-3 all done (10 fields encrypted, TEXT widths, FHIR read scoping) + R2-2 write-side audit (8 create/update endpoints closed); Item 4 (M2M FHIR access) deferred as design-only, no consumer exists. 151 tests pass (126 integration + 25 unit).**

> **Item 1 (2026-08-17): ✅ Done.** 10 fields encrypted across 6 entities (`MedicalHistoryEntry.description`, `AllergyEntry.allergen/reaction`, `Problem.notes`, `CarePlan.goal/interventions/notes`, `PriorAuth.notes`, `Referral.diagnosis/reason`); new columns widened to TEXT in schema.sql; seeds encrypt; 145 tests pass; smoke verified reads decrypt and the H2 file contains no plaintext PHI (only the `drug_allergy_class` CDS reference dictionary remains plaintext — by design, not patient PHI).

> **Item 2 (2026-08-17): ✅ Done.** Round 48's encrypted columns widened to TEXT: `appointment.chief_complaint`/`description`, `charge.notes`, `vital_sign.notes`, `referral.notes` (appointment.notes was already TEXT); stale `@Column(length = 500)` attributes dropped on the entity side. Verified: 145 tests pass; live smoke round-trips a 360-char note (ciphertext ~778 hex — would have truncated under VARCHAR(500)).

> **Item 3 (2026-08-17): ✅ Done.** All 4 FHIR read endpoints now honor DOCTOR scope via `DoctorPatientScope`: `GET /Patient/{id}` and `GET /Patient?_id=` call `requireAccess(patientId)`; `GET /Patient` (list) and `GET /Observation` (no patient param) filter through `findByIdIn(scope, ...)` when the caller is a DOCTOR; `GET /Observation/{id}` checks after resolving the row's `patientId` (404 wins over 403); `GET /Observation?patient=` calls `requireAccess`. Metadata stays public; the emergency break-glass exemption flows through the resolver. New repository methods: `PatientRepository.findByIdIn`, `ObservationRepository.findByPatientIdIn`. Tests (Order 80-82): doctor out-of-scope 403 for read/search/observation-id, doctor in-scope 200, admin 200, emergency token 200.

> **Item 1/2 regression tests + R2-2 write-side gap (2026-08-17): ✅ Done.** Test round-trips the encrypted free-text fields through the API (Order 83-84: care-plan `goal` ~250 chars — proves the TEXT widening holds, plus referral `diagnosis`/`reason`/`notes`), and audits the write side of R2-2: 8 create/update endpoints had silently missed `requireAccess` (`CarePlan/Problem/VitalSign/Immunization.create`, `PatientController.addHistory/addAllergy`, and the entire `ReferralController`/`PriorAuthController` — list without scope filter, create, update, `listByPatient`) — all fixed; Order 85 asserts doctor writes to patient 102 → 403 across all 8 endpoints. **151 tests pass (126 integration + 25 unit).**

## Background

Two items were left open after Round 48, plus one latent bug discovered during follow-up analysis.

### Item 1: Complete free-text PHI encryption (HIGH)

Round 48 encrypted the 6 fields named in finding R2-6, but the same category of free text (can embed identifiers) remains plaintext:

| Entity | Fields to encrypt |
|--------|-------------------|
| MedicalHistoryEntry | `description` |
| AllergyEntry | `allergen`, `reaction` |
| Problem | `notes` |
| CarePlan | `goal`, `interventions`, `notes` |
| PriorAuth | `notes` |
| Referral | `diagnosis`, `reason` (missed in R2-6) |

Not to encrypt: dictionary/coded labels (`snomedDisplay`, `loincDisplay`, `icd10Code`, `cptCodes`) — non-free-text.

**Verified safe**: no repository queries/sorts touch these columns (all patient-owned reads are by `patientId`); CDS allergy matching uses the already-encrypted `Patient.allergies` summary in memory — unaffected.

### Item 2: Round 48 latent bug — schema widths (HIGH)

The 6 columns encrypted in Round 48 (and the Item 1 columns) keep their plaintext-era widths. Ciphertext is hex ≈ `58 + 2×plaintext-length` chars, so e.g. `appointment.description VARCHAR(200)` now holds only ~70 plaintext chars — **long values silently truncate on write**. All newly encrypted columns must widen to `TEXT` (schema.sql).

### Item 3: FHIR read endpoints + DOCTOR scope (MEDIUM/HIGH)

`FhirPatientController` (`GET /Patient/{id}`, `GET /Patient?search`) and `FhirObservationController` (`GET /Observation/{id}`, `GET /Observation?patient=`) are ADMIN/DOCTOR but unscoped — inconsistent with the Round 47 REST model. Apply `DoctorPatientScope.requireAccess` (metadata stays public; emergency exemption flows through the resolver). Tests: doctor out-of-scope 403, admin 200, emergency token 200.

### Item 4: Machine-to-machine FHIR access — DEFERRED design (do not implement)

No M2M consumer exists today (Mirth uses the JSON API; no client-credentials flow; SMART-on-FHIR is a metadata declaration only). Design for when it becomes real:

- System accounts authenticate via OAuth2 client-credentials; JWT carries `scp: fhir/Patient.read` / `fhir/Observation.read`
- `DoctorPatientScope` exempts tokens holding the `fhir/*.read` scopes from patient scoping (system-level read)
- Human DOCTOR tokens keep patient scoping; PATIENT tokens already carry `patient/Patient.read` / `patient/Observation.read` scopes for a future patient-facing FHIR API

## Execution Plan

1. ✅ Add `@Convert` to the Item 1 fields; widen all affected columns (incl. Round 48's 6) to `TEXT` in schema.sql; encrypt the seed inserts
2. ✅ Dev H2 DB wipe + regenerate (plaintext rows would read as `[DECRYPT_FAILED]`)
3. ✅ `requireAccess` on the 4 FHIR read endpoints + integration tests
4. ✅ Verify: `mvn test` (151 pass), live smoke (long-text round-trip, FHIR 403/200/emergency)
5. ✅ Docs: API-LAYOUT (FHIR scope note), architecture doc (field inventory), this section
6. ✅ R2-2 write-side audit: `requireAccess` on every create/update (8 gaps found + fixed), encrypted round-trip regression tests (Orders 83-85)

# Round 49 follow-up: Post-Round Cross-Cutting Review ✅ Complete (2026-08-17)

> Full-project review after Round 49. Findings below are **identified, NOT fixed** — fixes were drafted, then reverted at the user's request (only the report is kept). Tracked here so a future round can pick them up.
>
> **Update (2026-08-18): all items FIXED on explicit user request** — backend: 4 free-text PHI fields encrypted (`@Convert` + TEXT widening + seed encryption), plaintext reason removed from the emergency-access log; round-trip regression tests added (Orders 86-87); 153 tests pass. Frontend: logout clears emergency break-glass session tokens, users edit form stale closure fixed, patient detail modal surfaces load failures (error banner), chat send failure shows inline error; `tsc --noEmit` + production build pass.

## Backend

| Severity | Finding | Location |
|----------|---------|----------|
| 🔴 HIGH | 4 free-text PHI fields still plaintext (Round 48/49 encrypted 16 fields, these were missed): `Immunization.notes`, `RefillRequest.reason` + `reviewNotes`, `CdsOverride.overrideReason`, `EmergencyAccess.reason` — should be `@Convert(AesAttributeConverter)` + column widened to TEXT + seed encryption | module/patient, module/prescription entities; schema.sql; DataInitializer |
| 🟡 HIGH | Emergency-access WARN log prints the plaintext `reason` (user-typed free text, can embed identifiers) — should log user/patient/expiry only | EmergencyAccessController.java |
| ⚪ LOW | Test count stale in docs: 151 written, actual was 153 (after the revert it is 151 again) | CLAUDE.md, docs/backend-architecture-explained.md |

## Frontend

| Severity | Finding | Location |
|----------|---------|----------|
| 🟡 HIGH | Logout does not clear emergency break-glass `sessionStorage` tokens (`emergencyToken`/`emergencyPatientId`) — a break-glass session can bleed into the next user's session | layout/StaffLayout.tsx handleLogout |
| 🟠 MEDIUM | Users edit form stale closure: `{ ...form, ...d }` builds the edit state from a stale `form` inside the async `getUserById` callback — races against user typing; should be `{ ...emptyForm, ...d, password: '' }` | views/system/users/index.tsx |
| 🟠 MEDIUM | Patient detail modal: 7 `.catch(() => {})` on history/allergy/vitals/problems/immunizations/care-plan/emergency loads — failure shows an empty modal with no feedback; should surface an error banner | views/patients/index.tsx |
| 🟠 MEDIUM | Chat send failure silently removes the optimistic bubble — no feedback to the user; should show an inline error | views/chat/index.tsx |

## Reviewed and confirmed correct (no change needed)

- Patient login / forgot-password use raw axios instead of `patientRequest` — correct: no token exists pre-auth, and `patientRequest`'s interceptor would fire the 401 refresh/redirect chain on the login endpoint itself (the interceptor's own refresh call also uses raw axios for the same reason).
- Unread-badge poll catches (StaffLayout/PatientLayout) are silent — cosmetic badge; failure degrades to "no badge" and the chat view surfaces errors itself.

## Note

- Fixes for all of the above were implemented and passed (153 tests) in commit `c3a2ce7`, then reverted in `043304a` per user instruction — review only, no code changes.

---

# Full-System Review III (2026-08-20): External Code Review — findings identified, NOT fixed

> Independent full-project review: backend `medical-server` (207 Java files), frontend `medical-web` (87 TS/TSX files), all `application*.yml` config. Findings below are **identified, NOT fixed** — review only, no code changes. Tracked here so a future round can pick them up.
>
> **Verdict: Blocked — 8 CRITICAL, 24 HIGH, 16 MEDIUM, 12 LOW.** Dominant critical classes: (1) audit aspect serializes `@Data` DTOs via `toString()` → plaintext passwords/refresh tokens/ePHI persist in the immutable 6-year `audit_log.detail`; (2) AES key rotation destroys all versioned ciphertext (no PREVIOUS_KEY fallback for v1 rows, migration skips `01%` rows); (3) staff `PatientController` omits `DoctorPatientScope` on getById/page/update → any doctor reads/edits any patient; (4) EPCS "transmit" is a logging stub that still marks `rx_status = transmitted` for controlled substances; (5) default profile is `h2` (public hardcoded JWT key + dev-mode + open H2 console); (6) prod auth self-contradiction (local HS256 decoder rejects Okta tokens) + JWT signing key falls back to the AES data key.

## Backend — Security / Compliance (🔴 CRITICAL)

| Severity | Finding | Location |
|----------|---------|----------|
| 🔴 CRITICAL | Audit log stores **plaintext passwords, refresh tokens, and PHI**: `AuditLogAspect.buildDetail` serializes `args[].toString()` when `phiAccess=false` (the default) and every `@Data` DTO's `toString()` includes secrets. Sites: staff login (`LoginRequest.password`), token refresh (`RefreshRequest.refreshToken`), patient login/reset/change-password (`PatientLoginRequest`, `ResetPasswordRequest` token+newPassword, `PatientPasswordChangeRequest`), `SysUserFormDTO.password`, chat `MessageFormDTO.content`, appointment chiefComplaint/notes, referral diagnosis/reason, emergency reason. Persisted in unencrypted `audit_log.detail` (VARCHAR 500), retained 2190 days | common/audit/AuditLogAspect.java:138-158, Auditable.java:19; module/system/controller/AuthController.java:22,28; module/patient/controller/PatientAuthController.java:67,137,154; module/system/service/SysUserService.java:55,67; module/chat/service/ChatService.java:41; module/appointment/service/AppointmentService.java:59,69; module/appointment/controller/ReferralController.java:62,86 |
| 🔴 CRITICAL | **AES key rotation destroys all versioned PHI**: `decrypt` tries only CURRENT_KEY for v1-prefixed rows (no PREVIOUS_KEY fallback); version byte is static `0x01` for both old and new keys; `KeyRotationService` migrates only `NOT LIKE '01%'` rows while all current data is v1; rotation is in-memory only and lost on restart (`init()` re-derives from the un-updated yml key) → after any rotate+restart all previously encrypted PHI reads as `[DECRYPT_FAILED]` and can be re-saved as the literal placeholder (permanent corruption) | common/config/AesCryptoUtil.java:131-144; common/job/KeyRotationService.java:132-134; common/config/KeyRotationController.java |
| 🔴 CRITICAL | Staff REST API broken access control: `PatientController.getById`/`update` call only `enforceEmergencyScope`, **no `doctorPatientScope.requireAccess`** (FHIR controller does it correctly); `page()` has no scope filter → any DOCTOR can enumerate the whole patient table and read/edit full PHI (name/DOB/address/insurance/medical history/allergies) | module/patient/controller/PatientController.java:49-54,76-81; module/patient/service/PatientService.java:24-34 |
| 🔴 CRITICAL | **Fake EPCS / false "transmitted"**: `transmit` calls `EpcsService.auditEpcsTransmission` — a stub (log only; 2FA, DEA cert, Surescripts all TODO per its own comment and ROADMAP) — then sets `rx_status = "transmitted"` and returns "NCPDP SCRIPT 10.6" XML that was never sent. Controlled substances marked transmitted without transmission: 21 CFR Part 1311 violation + patient-safety (Rx never reaches pharmacy). No `@Auditable`, no scope/status guard, no pharmacy EPCS capability check | module/prescription/controller/PrescriptionController.java:71-90; module/prescription/service/EpcsService.java:11-24 |
| 🔴 CRITICAL | Default seed credentials on every profile: `DataInitializer` (no `@Profile` guard) seeds `admin/admin123`, `doctor1/doctor123`, `patient1-4/patient123` whenever `sys_user` is empty — a fresh prod DB ships known login credentials | common/config/DataInitializer.java:58-87,193-203 |
| 🔴 CRITICAL | Refill request IDOR + wrong patient attribution: `create` only checks `existsById`, never that the prescription belongs to the requester; `r.setPatientId(loginUser.getUserId())` records the requester, not the owner → `approve`'s `requireAccess` validates the wrong patient; any patient can probe/spam refill requests on any prescription id | module/prescription/controller/RefillController.java:36-48,71-78 |

## Config & Deployment (🔴 CRITICAL)

| Severity | Finding | Location |
|----------|---------|----------|
| 🔴 CRITICAL | Default Spring profile is `h2`: no-profile deploy boots with dev-mode auth, hardcoded AES key (`h2-dev-key-...`), JWT fallback secret (`medical-dev-jwt-secret-key-for-local-development-only`), and permitAll `/h2-console/**` (empty password) — anyone can forge an ADMIN JWT with the public key and reach the DB console. Base yml also ships MySQL `root/root`; dev yml ships `medical/medical123` | application.yml:3,12-14; common/config/SecurityConfigDev.java:16-29; common/config/SecurityConfig.java:63-65; application-h2.yml |
| 🔴 CRITICAL | Production auth is self-contradictory: `SecurityConfigProd` registers a local HS256 `JwtDecoder`, overriding the Okta issuer-uri JWKS auto-config → Okta-issued RSA staff tokens are rejected (all prod staff calls 401) while locally-signed patient/emergency tokens pass. `@Value("${JWT_SIGNING_KEY:${AES_KEY:}}")` reuses the AES data key as the JWT signing key when unset — one compromise defeats PHI-at-rest AND authentication | common/config/SecurityConfigProd.java:24,44-52; module/system/service/AuthService.java:89-97; application.yml:30-33 |

## Backend — 🟡 HIGH (bugs / data integrity)

| Severity | Finding | Location |
|----------|---------|----------|
| 🟡 HIGH | CDS runs **after** save and never blocks: prescription + items persisted before `checkDrugInteractions`/`checkAllergyContraindications`, warnings only `log.warn`-ed; `CdsOverride` (override audit) is dead code — never written; severe interactions/contraindicated allergies save silently | module/prescription/service/PrescriptionService.java:90-108; module/prescription/repository/CdsOverrideRepository.java |
| 🟡 HIGH | Prescriber identity client-supplied: `doctorId`/`prescriberNpi`/`deaNumber` come from the request body, not the authenticated `LoginUser` → attribution/DEA forgery on prescriptions (incl. controlled substances) | module/prescription/service/PrescriptionService.java:74-86; module/prescription/dto/PrescriptionFormDTO.java:17,31-33 |
| 🟡 HIGH | eCQM measures always compute 0: measure SQL does `LOWER(medical_history) LIKE '%diabetes%'` and `TIMESTAMPDIFF(YEAR, date_of_birth,...)` on **AES-encrypted columns**; `executeCount` swallows all errors → silent zero; CMS122 numerator logic inverted (counts HbA1c ≤ 9, not > 9); CMS125 exclusion wrong | common/config/DataInitializer.java:550-584; module/quality/service/QualityMeasureService.java:123-131 |
| 🟡 HIGH | Appointment double-booking TOCTOU: check-then-insert with no DB constraint / pessimistic lock / `@Version` guard; conflict window fixed ±30 min ignoring `duration`; no patient-vs-patient conflict check; `create` skips patient-scope; `update` can reassign to an out-of-scope patient; `delete` allows deleting terminal/completed appointments | module/appointment/service/AppointmentService.java:60-87,116-132; module/appointment/controller/AppointmentController.java:62-67 |
| 🟡 HIGH | Refill approval is a no-op: `approve` only flips status — no refill-budget check/decrement, no CDS re-run, no dispensing trigger; unlimited duplicate PENDING requests allowed | module/prescription/controller/RefillController.java:32-48,67-78 |
| 🟡 HIGH | Prescription item validation nonexistent: `PrescriptionItemDTO` has zero constraints, `items` list lacks `@Valid` cascade → negative quantity/daysSupply/refills, 11 refills on Schedule II, empty drug names accepted; prescription PHI (diagnosis, drugName, dosage, sig) plaintext at rest (only DEA encrypted) | module/prescription/dto/PrescriptionItemDTO.java; module/prescription/entity/Prescription.java:27-59 |
| 🟡 HIGH | Billing: `pay()` allows overpayment → negative balance; `denyClaim` allows denying DRAFT and partially-paid PENDING bills (no refund handling); PAID bills deletable; prior-auth has no expiry and is never enforced at submit; Charge→Bill concurrent conversion race → orphan bills; **no BILLING role exists anywhere** (billing endpoints ADMIN-only) | module/billing/service/BillService.java:119-150; module/billing/entity/PriorAuth.java; module/billing/service/ChargeService.java:60-81; common/config/DataInitializer.java:84-97 |
| 🟡 HIGH | Emergency break-glass token is a full DOCTOR token (30 min): `scope=EMERGENCY`/`patientId` restriction enforced in only 2 controllers; all other modules treat it as plain `ROLE_DOCTOR`; `DoctorPatientScope.resolve` even adds the holder's own historical patients | module/system/controller/EmergencyAccessController.java:52-63; common/security/DoctorPatientScope.java:44-47 |
| 🟡 HIGH | Profile re-auth bypass: `UserProfileController.isAnyDifferent` treats null as "not different" yet null values are applied → hijacked session can clear NPI/license/DEA without current password; password history excludes the current password (immediate reuse); no admin password-reset path (update requires `@NotBlank` password but `applyTo` never sets it); no patient password-change endpoint for PATIENT role | module/system/controller/UserProfileController.java:55-89,102-119; module/system/service/SysUserService.java:69-78 |
| 🟡 HIGH | Role privilege escalation: `SysRoleController` class-level `hasRole('ADMIN') or hasAuthority('system:role:list')` lets a read-only permission create/update/delete roles | module/system/controller/SysRoleController.java:17 |
| 🟡 HIGH | SSE: `EMITTERS.put` overwrites a user's existing emitter and the old emitter's onCompletion removes the NEW one (multi-tab message loss); open SSE streams (up to 30 min) keep pushing decrypted message PHI after force-logout/disable | module/chat/controller/ChatSseController.java:57-92 |
| 🟡 HIGH | Cross-patient IDOR in updates: `CarePlanController`/`ProblemController` check scope against the path patientId but never verify the loaded resource belongs to it → doctor scoped to patient A can modify patient B's care plan/problem; both update endpoints also silently drop most submitted fields (title/goal/interventions or snomed/icd/onsetDate) | module/patient/controller/CarePlanController.java:61-72; module/patient/controller/ProblemController.java:62-74 |
| 🟡 HIGH | FHIR: `_count=0` → division by zero (500); `bundle.setTotal` set twice (real total overwritten); duplicate `GET /api/v1/fhir/metadata` mapping in two controllers (startup ambiguity); Observation `status` hardcoded FINAL (amended/corrected results mislabeled); `DateTimeType(effectiveDate + ":00")` invalid when the datetime already has seconds | module/patient/controller/FhirPatientController.java:90-129; common/config/FhirConfig.java:34; module/patient/controller/FhirObservationController.java:80-119 |
| 🟡 HIGH | Patient forgot-password non-functional in prod: reset tokens in an in-memory `ConcurrentHashMap` (lost on restart); prod path neither logs nor emails the token and no mailer exists | module/patient/controller/PatientAuthController.java:46,113-151 |
| 🟡 HIGH | Audit tamper-evidence is write-only: `row_hash` computed at `@PrePersist`, never verified on read, no hash chaining, not exposed in `AuditLogVO`, no `@Version` → direct DB edits to audit rows undetectable (21 CFR Part 11 gap); `detail` column VARCHAR(500) can truncate and silently drop audit rows in the async writer | common/audit/AuditLog.java:39-74; common/audit/AuditLogService.java:27-61; resources/sql/schema.sql (audit_log) |
| 🟡 HIGH | Refill/transmit flow lacks CDS re-check and Schedule-II vs III+ branching; `PharmacyDirectory.supportsEpcs` never consulted before "transmitting" a controlled script to a non-EPCS pharmacy | module/prescription/service/EpcsService.java:11-13; module/prescription/controller/PrescriptionController.java:71-90 |

## Frontend — 🟡 HIGH (bugs / data loss)

| Severity | Finding | Location |
|----------|---------|----------|
| 🟡 HIGH | JWTs + 30-day refresh tokens stored in `localStorage` (XSS-exfiltratable) and patient PHI (`patientInfo`) cached alongside; no CSP header set by the backend → any injected script exfiltrates a full session + PHI | api/request.ts:17; api/patientRequest.ts:9; utils/auth.ts:26; views/login/index.tsx:17-20; views/patient/layout/PatientLayout.tsx:21 |
| 🟡 HIGH | **Editing a patient silently wipes `medicalHistory`/`allergies`**: frontend `PatientForm` lacks both keys, backend PUT is a full replace (`applyTo` unconditionally sets them) → every patient edit permanently erases encrypted PHI; same pattern wipes appointment `icd10Codes`/`notes` (downstream bills lose ICD codes) | views/patients/index.tsx:316-324; module/patient/dto/PatientFormDTO.java:134-135; views/appointments/index.tsx:66-77; module/appointment/dto/AppointmentFormDTO.java:53,59 |
| 🟡 HIGH | "Edit Prescription" creates a duplicate prescription: UI has an Edit form but backend has no PUT endpoint (`api/prescription.ts` lacks update) → submit POSTs a brand-new script, original untouched | views/prescriptions/index.tsx:67-73,105-128 |
| 🟡 HIGH | Staff "Edit User" always fails 400: frontend sends `password: ''` while `SysUserFormDTO.password` is `@NotBlank @ValidPassword` → admins cannot edit users or reset passwords | views/system/users/index.tsx:49; module/system/dto/SysUserFormDTO.java:16-18 |
| 🟡 HIGH | Staff profile save silently fails on credential changes: backend requires `currentPassword` for NPI/license/specialty edits, form doesn't send it and mutation has no `onError` → users believe saves succeeded | views/profile/index.tsx:31-37,70; module/system/controller/UserProfileController.java:58-68 |
| 🟡 HIGH | Silent mutation failures across billing/prescriptions/patient portal (no `onError`): failed payment/deny/transmit/adjudicate/submit give no feedback; several `await` calls without try/catch cause unhandled rejections | views/billing/index.tsx:75-108; views/prescriptions/index.tsx:40-91; views/patient/appointments/index.tsx:22-28; views/patients/index.tsx:383-388 |
| 🟡 HIGH | No validation on numeric medical fields: vitals (BP 9999, temp −300) and dose fields accept garbage → `NaN` sent as `null` silently; empty DOB string → Jackson 400 on patient create; `Number('')` → 0 → misleading `@Positive` errors | views/patients/index.tsx:448-466,316; views/prescriptions/index.tsx:292-294; views/charges/index.tsx:64-75 |
| 🟡 HIGH | CSV export formula injection: `csv()` escapes commas/quotes/newlines but not leading `= + - @` → Excel formula injection on exported PHI; any scoped doctor can bulk-export full name/address/medical history (only phone/email/claim# masked); per-IP rate limit only, O(N) DB reads | module/export/controller/ExportController.java:41-74,130-137 |

## 🟠 MEDIUM

| Severity | Finding | Location |
|----------|---------|----------|
| 🟠 MEDIUM | Consent is recorded but never enforced: `ConsentRepository.findByPatientIdAndConsentTypeAndStatus` has zero callers; no read path checks consent/restrictions before returning PHI | module/patient/controller/ConsentController.java; module/patient/repository/ConsentRepository.java:12 |
| 🟠 MEDIUM | PHI reads not audited: no `@Auditable` on `PatientService.getById`, FHIR reads, portal vitals/problems/immunizations/care-plans; clinical CUD annotations missing `phiAccess=true` → plaintext ePHI in audit detail (same root cause as CRITICAL above) | module/patient/service/PatientService.java:37-41; module/patient/controller/PatientPortalController.java:312-340; module/patient/controller/{CarePlan,Problem,Immunization,VitalSign}Controller.java |
| 🟠 MEDIUM | Structured clinical PHI plaintext at rest: `observation.obs_value`, `vital_sign` measurements, `care_plan.title`, `problem` snomed/icd displays, immunization vaccine/lot — only free-text notes encrypted | module/patient/entity/{Observation,VitalSign,CarePlan,Problem,Immunization}.java |
| 🟠 MEDIUM | Redis cache leaks: `SysUserVO.email` not annotated `@PhiField` → plaintext PHI email cached; `PhiMaskingRedisSerializer` enables Jackson `DefaultTyping.NON_FINAL` (gadget risk on compromised Redis); no Redis password/TLS in config | common/config/PhiMaskingRedisSerializer.java:26-28; module/system/dto/SysUserVO.java:19-21; application.yml (data.redis) |
| 🟠 MEDIUM | `[DECRYPT_FAILED]` placeholder flows into UI and can be re-encrypted on the next save → permanent ciphertext corruption (amplified by the rotation defect) | common/config/AesCryptoUtil.java:127-129,148; module/patient/controller/PatientPortalController.java:106-127 |
| 🟠 MEDIUM | Pagination `page=0` → `PageRequest.of(-1)` → 500 across most list endpoints; `size` unbounded; `PageQuery` has no `@Min`/`@Max` | module/**/*Service.java, controller pages; common/base/PageQuery.java |
| 🟠 MEDIUM | Timezone inconsistency: `America/Chicago` (base/prod) vs `America/Los_Angeles` (dev) vs UTC (`AuditLogWriter`) vs `ZoneId.systemDefault()` (`JwtClaimMapper`) vs JVM-local "today" (`DashboardService`) | application*.yml; security/JwtClaimMapper.java:42; common/audit/AuditLogWriter.java:42 |
| 🟠 MEDIUM | N+1 queries: `AppointmentService.toVO` (2/appt), `ChatService.getConversations` (~3/conversation), `SysUserService.page` (role query per user), portal VO mappers | module/appointment/service/AppointmentService.java:134-140; module/chat/service/ChatService.java:91-103; module/system/service/SysUserService.java:40-42 |
| 🟠 MEDIUM | `DataRetentionJob` half-implemented: 5 injected repositories unused, `softDeleteRetentionDays` unused, audit "archive" is only a flag flip in the same table; archived rows grow forever | common/job/DataRetentionJob.java:23-34 |
| 🟠 MEDIUM | Rate limiter keys are per-IP only (NAT/proxy = office-wide lockout) and Redisson limiter keys never expire (Redis growth); export limiter message hardcodes "5" regardless of configured value | common/config/RateLimiterConfig.java:34,61,85,110 |
| 🟠 MEDIUM | Session controls minimal: 2h access token, no idle/concurrent-session limits; patient tokens not invalidated on password change; staff logout revokes nothing (Okta side assumed) | application.yml:47; module/patient/controller/PatientAuthController.java:101-111,153-194 |
| 🟠 MEDIUM | Login failure responses distinguish locked/disabled vs bad credentials (account enumeration) | module/system/service/AuthService.java:67-75; module/patient/controller/PatientAuthController.java:74-82 |
| 🟠 MEDIUM | Swagger UI `/doc.html` and API docs permitAll publicly (PHI system endpoint disclosure); FHIR controllers excluded from docs (inconsistent) | common/config/SecurityConfig.java:62; common/config/SpringDocConfig.java |
| 🟠 MEDIUM | `myDisclosures` returns raw `AuditLog` entities to patients — exposes staff usernames/IPs/rowHash; portal vitals/problems/etc. return raw entities instead of VOs | module/patient/controller/PatientPortalController.java:342-354,312-340 |
| 🟠 MEDIUM | Dependency drift: Querydsl declared in pom/CLAUDE.md but zero usages (Specifications used); knife4j version property dead; springdoc property 2.6.0 vs actual 2.7.0; successful-login audit rows have null user_id | pom.xml; CLAUDE.md; common/audit/AuditLogAspect.java:106-112 |
| 🟠 MEDIUM | ADT/lab ingestion: ADT `VisitInfo` fields never used; lab ingestion returns ACK even when the MRN is unknown (sender believes it worked); `LabAnalysisService.autoFlag` is dead code | module/integration/service/{AdtService,LabResultService}.java |

## ⚪ LOW

- Magic numbers: lock threshold `5` hardcoded in JPQL, `PASSWORD_HISTORY_LIMIT=3`, hardcoded `$90/$100` appointment charges, `AppointmentService` writes chief-complaint text into `icd10Codes`.
- Raw entity responses in several endpoints (violates "never raw entities" rule).
- MRN search `%keyword%` LIKE without escaping `%`/`_` (wildcard injection).
- `enforceEmergencyScope` duplicated verbatim in two controllers.
- Appointment `status` is an untyped int magic-value (0/2/3/4) with no constants/enum.
- `SysMenuVO.buildTree` O(n²); `SysRoleService.update` potential NPE on null roleCode.
- Patient logout double-audits (aspect + manual `writeAsync`).
- Audit param-name resolution depends on `-parameters` compiler flag.
- `EmergencyAccessController.history` returns raw entities including decrypted reason, hard 500-row cap.
- `PatientCaseService` null concatenation renders "null 2 x7d" / "Practitioner/null".
- SSE ticket lands in proxy/access logs (single-use, 30s TTL — acceptable).
- Vitals rendering `systolicBp != null` shows "120/" when diastolic is null.

## Reviewed and confirmed correct (no change needed)

- PHI field encryption coverage is strong (SSN/name/DOB/address/phones/email/insurance/history/allergies/DEA/chat content all via `@Convert(AesAttributeConverter)`); no Service does manual encrypt/decrypt wrappers.
- No raw SQL with user input — all `@Query` are parameterized JPQL.
- Every endpoint has `@PreAuthorize`; patient portal consistently self-scopes via `loginUser.getUserId()` (no IDOR in the portal).
- All currency is `BigDecimal` (no double/float money anywhere).
- `BaseEntity` provides `@Version` + `@SQLDelete`/`@SQLRestriction` consistently (exception: bulk JPQL delete in `PrescriptionItemRepository`).
- BCrypt + 5-strike lockout + per-IP login rate limiting on both staff and patient auth; password reset rate-limited and single-use.
- SSE uses single-use 30s tickets so the JWT never appears in subscribe URLs.
- Frontend falsy-safety rules followed (`!== '' ? Number(x) : null`, `??` for display); no XSS sinks (`dangerouslySetInnerHTML` absent), no commented-out code, all modals stop propagation, no hardcoded credentials in the frontend.
- Export masks phone/email/claim-number; FHIR Patient masks SSN.
- Redis PHI cache masking (`@PhiField`) works for the fields that are annotated (gap: email, see MEDIUM).

## Note

- Review only — no code changes were made. Recommended fix order: (1) audit detail redaction + `phiAccess` defaults; (2) default profile → `prod` + seed-data gating + H2 console auth; (3) `PatientController` doctor scope; (4) EPCS "transmitted" claim; (5) key-rotation decrypt fallback; (6) prod auth model + independent `JWT_SIGNING_KEY`; then the HIGH data-loss cluster (patient/appointment edit wipes, prescription duplicate edit, user edit 400).

---

# Fix Batch 1: Audit & Credential Security (Review III C1) ✅ Complete (2026-08-20)

> First fix batch for Full-System Review III. Goal: no plaintext passwords / refresh tokens / ePHI in the immutable `audit_log.detail`, and complete the user_id on login audit rows.

## Changes

| # | Change | Files |
|---|--------|-------|
| 1.1 | `AuditLogAspect.buildDetail` rewritten: never calls `toString()` on request DTOs. Complex args are reflected field-by-field with a `SENSITIVE_FIELD_NAMES` blacklist (password/token/refreshToken/content/diagnosis/reason/notes/chiefComplaint/description/medicalHistory/allergies/ssn/dea/claim/phone/email/...) redacted as `[REDACTED]`; simple values truncated to 50 chars; detail capped at 1500; `phiAccess=true` still masks every value as `[PHI]`. Exposed `describeArg` package-visible for tests | common/audit/AuditLogAspect.java |
| 1.2 | `phiAccess = true` added to every audited method whose args carry credentials or ePHI: staff login/refresh, patient login/reset/refresh/change-password, user create/update, profile update, chat send, appointment create/update, referral create/update, emergency access, care-plan/problem/immunization/vital-sign create/update, patient history/allergy add, bill create/adjudicate/pay/deny, charge create, prior-auth create/update, refill create/deny, prescription create | module/system/controller/AuthController.java; module/patient/controller/PatientAuthController.java, PatientPortalController.java, PatientController.java, CarePlanController.java, ProblemController.java, ImmunizationController.java, VitalSignController.java; module/system/controller/UserProfileController.java; module/system/service/SysUserService.java; module/chat/service/ChatService.java; module/appointment/service/AppointmentService.java, controller/ReferralController.java; module/system/controller/EmergencyAccessController.java; module/billing/service/BillService.java, ChargeService.java, controller/PriorAuthController.java; module/prescription/service/PrescriptionService.java, controller/RefillController.java |
| 1.3 | `audit_log.detail` widened VARCHAR(500) → VARCHAR(2000) (schema + entity `@Column(length=2000)`) so multi-arg details no longer truncate and silently drop audit rows | resources/sql/schema.sql; common/audit/AuditLog.java |
| 1.4 | Login-audit `user_id`/`patient_id` fallback: `resolveUserId`/`resolvePatientId` now unwrap `Result.getData()` and read `userId`/`patientId` from the response payload (permitAll login/refresh have no `Authentication`); `-parameters` compiler flag added so `signature.getParameterNames()` resolves real names | common/audit/AuditLogAspect.java; pom.xml |

## Tests

- New `AuditLogAspectTest` (3 cases): sensitive fields redacted on `@Data`-style DTOs, simple values/null preserved, long values truncated.
- `mvn test`: **156 tests, 0 failures** (153 prior + 3 new).

## Verified

- `mvn compile` + `mvn test` green.
- Manual trace: `LOGIN_SUCCESS` audit detail is now `login([PHI])` (no password); non-PHI methods serialize field-by-field with secrets redacted.

## Notes

- Double defense: (a) `phiAccess=true` on credential/PHI methods, (b) field-level blacklist redaction for any future `@Auditable` that misses the flag.
- Blacklisting `notes`/`note`/`reason`/`content` etc. intentionally reduces detail verbosity for clinical free text — audit still records user, patient, module, action, targetId, IP, timestamp.
- Remaining batches (2–6) tracked in the fix plan; next: Batch 2 (deployment security — Review III C5/C6/C7).

---

# Fix Batch 2: Deployment Security (Review III C5/C6/C7) ✅ Complete (2026-08-20)

> Second fix batch. Goal: no default-to-insecure startup, no hardcoded keys/credentials, no dev-mode leak into prod, and a coherent prod token trust model.

## Changes

| # | Change | Files |
|---|--------|-------|
| 2.1 | Default Spring profile removed (`spring.profiles.active: h2` → `${SPRING_PROFILES_ACTIVE:}`). New `ProdGuard` (@PostConstruct) fails fast when: no active profile, unsupported profile, prod runs with dev-mode, prod misses AES_KEY/JWT_SIGNING_KEY/DB_USER/DB_PASSWORD, or JWT_SIGNING_KEY == AES_KEY (key separation) | application.yml; common/config/ProdGuard.java (new) |
| 2.2 | `SecurityConfigDev` no longer has a hardcoded fallback JWT key — dev/h2 must configure `app.security.dev-jwt-secret` explicitly or startup fails | common/config/SecurityConfigDev.java |
| 2.3 | H2 console now requires both the h2 profile AND explicit `app.security.h2-console-enabled: true` (set in application-h2.yml) | common/config/SecurityConfig.java; application-h2.yml |
| 2.4 | `DataInitializer` gated with `@Profile({"dev","h2"})` — seed users/patients (admin/admin123 etc.) never reach a fresh prod DB | common/config/DataInitializer.java |
| 2.5 | Plaintext DB credentials removed: base yml `root/root` → `${DB_USER}`/`${DB_PASSWORD}`; dev yml `medical/medical123` → `${DB_USER:medical}`/`${DB_PASSWORD:medical123}` | application.yml; application-dev.yml |
| 2.6 | Prod token trust model: `SecurityConfigProd` now exposes a `CompositeJwtDecoder` (new) that routes by issuer — Okta-issued staff tokens → IdP JWKS decoder; locally-issued patient/emergency/refresh tokens → local HS256 decoder with strict issuer validation (3 issuers). `JWT_SIGNING_KEY` no longer falls back to AES_KEY (min 32 chars, enforced). `JwtClaimMapper` rejects any token carrying the `refresh` scope as an access token. Not using `DelegatingJwtDecoder` (absent from the resolved Spring Security 6.4 jars) | common/config/SecurityConfigProd.java; common/config/CompositeJwtDecoder.java (new); security/JwtClaimMapper.java |

## Tests

- New `ProdGuardTest` (5 cases): no-profile fail, unsupported-profile fail, prod-missing-secrets fail, AES==JWT key reuse fail, h2 pass.
- `mvn test`: **161 tests, 0 failures** (156 prior + 5 new).

## Verified

- `mvn compile` + `mvn test` green; ProdGuard logs "Deployment guard passed for profile(s): h2" in the test context.
- Manual trace: prod boot without `SPRING_PROFILES_ACTIVE` → `IllegalStateException` from ProdGuard; prod with `JWT_SIGNING_KEY == AES_KEY` → rejected; h2/dev still boot with explicit dev-jwt-secret.

## Notes

- Behavior change (ops): production must now set `SPRING_PROFILES_ACTIVE=prod` plus `AES_KEY`, `JWT_SIGNING_KEY` (independent, ≥32 chars), `DB_USER`, `DB_PASSWORD`. Documented in application.yml comments.
- Remaining batches tracked in the fix plan; next: Batch 3 (access control — Review III C3/C8 + IDOR cluster).

---

# Fix Batch 3: Access Control (Review III C3/C8 + IDOR cluster) ✅ Complete (2026-08-20)

> Third fix batch. Goal: doctor patient-scoping on the staff REST API, cross-patient mutation guards, and a correct refill ownership/refill-budget flow.

## Changes

| # | Change | Files |
|---|--------|-------|
| 3.1 | `PatientController.getById`/`update` now call `doctorPatientScope.requireAccess(id)`; `page()` filters by `doctorPatientScope.resolve()` (ADMIN unscoped, DOCTOR sees only their patients). `PatientService.page` gained a `scopedPatientIds` parameter with an id-IN predicate | module/patient/controller/PatientController.java; module/patient/service/PatientService.java |
| 3.2 | `CarePlanController.update` / `ProblemController.update`: reject when the loaded resource does not belong to the path patientId (cross-patient IDOR); both updates now apply the full field set (title/goal/interventions/startDate/targetDate/… and snomed/icd/onsetDate/…), fixing the silent-field-drop bug | module/patient/controller/CarePlanController.java; module/patient/controller/ProblemController.java |
| 3.3 | `RefillController.create`: loads the prescription, requires it belongs to the requesting patient (`p.getPatientId().equals(loginUser.getUserId())`), requires `rx_status = active`, dedups PENDING requests (`existsByPrescriptionIdAndStatus`), and records the prescription owner (not the requester) as patientId. `approve`: consumes one refill from the prescription items (rejects when none remain). Bare `orElseThrow()` → 404 BusinessException | module/prescription/controller/RefillController.java; module/prescription/repository/RefillRequestRepository.java |
| 3.4 | `AppointmentService.create` requires access to the target patient; `update` re-scopes when `patientId` is reassigned (applyTo overwrites it); `delete` requires access and refuses completed/no-show appointments | module/appointment/service/AppointmentService.java |
| 3.5 | `PrescriptionService.create` requires access to the target patient | module/prescription/service/PrescriptionService.java |
| 3.6 | Bare `orElseThrow()` → 404 BusinessException in Referral/PriorAuth/CarePlan/Problem updates | module/appointment/controller/ReferralController.java; module/billing/controller/PriorAuthController.java; module/patient/controller/{CarePlan,Problem}Controller.java |

## Tests

- Existing `IntegrationTest` refill round-trip updated (fixture marks prescription 300 completed; refill now requires active — uses 301).
- `cleanup-test-data.sql` clears `refill_request` between runs.
- `mvn test`: **161 tests, 0 failures**.

## Verified

- `mvn compile` + `mvn test` green.
- Manual trace: DOCTOR token → `GET /api/v1/patients/{otherDoctor'sPatient}` → 403; patient refill on another patient's prescription → 403; duplicate PENDING refill → 409; refill approve without remaining refills → 409.

## Notes

- Behavior change: doctors can no longer enumerate/search the full patient table — matches the FHIR read scoping added in Round 49.
- Remaining batches tracked in the fix plan; next: Batch 4 (prescriptions & CDS — Review III C4 + prescription HIGH cluster).

---

# Fix Batch 4: Prescriptions & CDS (Review III C4 + prescription HIGH cluster) ✅ Complete (2026-08-20)

> Fourth fix batch. Goal: no false "transmitted" claims for controlled substances, server-derived prescriber identity, CDS enforced before persistence with an override audit trail, validated prescription items, and prescription PHI encrypted at rest.

## Changes

| # | Change | Files |
|---|--------|-------|
| 4.1 | EPCS fail-closed: `EpcsService.assertTransmissionSupported` rejects controlled-substance prescriptions until a real 21 CFR Part 1311 channel exists (also checks `pharmacy.supportsEpcs`). `transmit` no longer marks `rx_status = transmitted` — non-controlled scripts become `generated` (draft XML), return format "NCPDP SCRIPT (draft)", added scope check + active-status guard + `@Auditable` + `@Transactional` | module/prescription/service/EpcsService.java; module/prescription/controller/PrescriptionController.java |
| 4.2 | Prescriber identity server-derived: `doctorId`, `prescriberNpi`, `deaNumber` now come from the authenticated `LoginUser`'s `SysUser` profile, never the request body | module/prescription/service/PrescriptionService.java; module/prescription/controller/PrescriptionController.java |
| 4.3 | CDS enforced BEFORE persistence: items are built in memory, drug-drug + active-medication + allergy checks run first; `severe`/`contraindicated` warnings block the save unless `overrideReason` is supplied — overrides are persisted to `cds_override` (previously dead code). `PrescriptionFormDTO.overrideReason` added | module/prescription/service/PrescriptionService.java; module/prescription/dto/PrescriptionFormDTO.java |
| 4.4 | Prescription item validation: `@NotBlank` drugName/dosage/route/frequency, `@Positive` duration/daysSupply/quantity, `@PositiveOrZero` refills/daw/unitPrice, `@Valid` cascade on the items list. New `CdsService.checkActiveMedicationInteractions` (new Rx vs patient's other active Rxs) and cross-reactive allergy codes (`DrugAllergyClass.crossReactiveCodes`) now evaluated | module/prescription/dto/PrescriptionItemDTO.java; module/prescription/service/CdsService.java; module/prescription/repository/PrescriptionRepository.java |
| 4.5 | Prescription PHI encrypted at rest: `diagnosis`, `icd10Codes`, `pharmacyName`, `pharmacyPhone` (Prescription) and `drugName`, `dosage`, `sig`, `notes` (PrescriptionItem) now `@Convert(AesAttributeConverter)`; schema columns widened to TEXT; seed data encrypted | module/prescription/entity/{Prescription,PrescriptionItem}.java; resources/sql/schema.sql; common/config/DataInitializer.java |
| 4.6 | NCPDP claim corrected — no "10.6" compliance claim (draft only) | module/prescription/controller/PrescriptionController.java |

## Tests

- `prescriptionTransmit_shouldGenerateNcpdp` updated: uses active fixture prescription 301, expects `status == "generated"`.
- `cleanup-test-data.sql`: restores 301/302 to `active`; prescription_item fixture rows now store pre-generated AES ciphertexts (match the encrypted entity) — removes the `AEADBadTagException` noise and `[DECRYPT_FAILED]` fixtures.
- Stale H2 file DB (pre-encryption plaintext) deleted to force clean reseed.
- `mvn test`: **161 tests, 0 failures**.

## Verified

- `mvn compile` + `mvn test` green, no AEAD errors.
- Manual trace: transmit on a `completed` script → 409; transmit controlled-schedule script → 409 (fail-closed); CDS severe/contraindicated without overrideReason → 409; with overrideReason → saved + `cds_override` row.

## Notes

- Behavior change (API): `PUT /prescriptions/{id}/transmit` now returns `status: "generated"` and refuses controlled substances. Frontend displays `rxStatus` verbatim — verify labels in Batch 5.
- Remaining batches tracked in the fix plan; next: Batch 5 (data integrity — frontend data-loss bugs, eCQM, double-booking, audit tamper-evidence).

---

# Fix Batch 5: Data Integrity (Review III frontend HIGH cluster + eCQM + double-booking + audit chain) ✅ Complete (2026-08-20)

> Fifth fix batch. Goal: no silent data loss on edits, working eCQM measures, serialized appointment booking, tamper-evident audit logs, and user-visible mutation errors.

## Changes

| # | Change | Files |
|---|--------|-------|
| 5.1 | Patient form now carries `medicalHistory`/`allergies` (type + emptyForm + editable textareas) so a PUT never nulls them; appointment form carries `icd10Codes`/`notes` (type + backfill + inputs); prescription "Edit" became **view-only** (prescriptions are immutable clinical records — previously it POSTed a duplicate); user edit uses the new `SysUserUpdateFormDTO` (password optional — blank keeps current, non-blank resets it via password_history, fixing the always-400 edit and adding admin password reset); profile update collects `currentPassword` when credentials change and adds `onError` | medical-web/src/{types/entities.ts, views/patients/index.tsx, views/appointments/index.tsx, views/prescriptions/index.tsx, views/profile/index.tsx}; medical-server/module/system/dto/SysUserUpdateFormDTO.java (new); module/system/{service/SysUserService.java, controller/SysUserController.java} |
| 5.2 | eCQM measures rewritten to evaluate decrypted entities in memory (CMS122 HbA1c poor control >9% incl. no-record = poor, CMS125 mammogram ≤27 months + deceased exclusion, CMS165 most-recent BP <140/90). No more raw SQL against AES-encrypted columns silently returning 0 | module/quality/service/QualityMeasureService.java |
| 5.3 | Appointment double-booking serialized: new `appointment_lock` table + `AppointmentLockRepository` (INSERT…ON DUPLICATE KEY upsert + `SELECT … FOR UPDATE`), `create`/`update` lock the doctor's row before the conflict check so concurrent bookings cannot both pass (TOCTOU closed) | module/appointment/entity/AppointmentLock.java (new); repository/AppointmentLockRepository.java (new); service/AppointmentService.java; resources/sql/schema.sql |
| 5.4 | Audit tamper-evidence: `audit_log.prev_hash` column; rows hash prevHash + content (hash chaining); writer links each row to the previous row's hash; `AuditLogVO` exposes rowHash/prevHash; new ADMIN `GET /api/v1/audit-logs/verify` checks the whole chain and reports the first broken row | common/audit/{AuditLog.java, AuditLogWriter.java, AuditLogService.java, AuditLogVO.java, AuditLogController.java, repository/AuditLogRepository.java}; resources/sql/schema.sql |
| 5.5 | `onError` added to all 17 mutations that were silently failing across billing, prescriptions, patient portal, quality measures (subagent-assisted, tsc-verified) | medical-web/src/views/{billing,prescriptions,patient/appointments,patient/bills,patient/prescriptions}/index.tsx; system/QualityMeasures.tsx |
| 5.6 | Numeric guard on vital signs (non-empty fields must parse, no more NaN→null); DOB input `type="date"` + empty DOB sent as null (no more Jackson 400); CSV export escapes leading `= + - @` (formula-injection guard) | medical-web/src/views/patients/index.tsx; module/export/controller/ExportController.java |

## Verified

- `mvn test`: **161 tests, 0 failures**.
- `npx tsc --noEmit` clean; `npm run build` (Vite production) succeeds.
- Manual trace: patient edit preserves medicalHistory/allergies; appointment edit preserves icd10Codes/notes; eCQM calculate returns non-zero denominators on seed data; audit-logs/verify reports `intact: true`.

## Notes

- `GET /api/v1/audit-logs/verify` added — document in API-LAYOUT.
- Behavior change: prescriptions can no longer be edited (view-only) — matches clinical-record immutability; a future PUT could be added if business requires corrections with an audit trail.
- Remaining: Batch 6 (hardening & cleanup — MEDIUM/LOW items).

---

# Fix Batch 6: Hardening & Cleanup (Review III MEDIUM/LOW) ✅ Complete (2026-08-20)

> Final fix batch. Goal: shrink the credential-exfiltration surface, audit PHI reads, tame pagination, prevent `[DECRYPT_FAILED]` corruption, and remove dead dependencies.

## Changes

| # | Change | Files |
|---|--------|-------|
| 6.1 | Token storage migrated from `localStorage` to **sessionStorage** (JWT/refresh tokens + cached `patientInfo` no longer persist across tabs/restarts; reads fall back once for migration). New `tokenStore` helper + `readPatientInfo()` (try/catch — no more white-screen on corrupt cache) in utils/auth.ts; all 12 read/write sites updated | medical-web/src/utils/auth.ts (new helpers); api/{request,patientRequest}.ts; App.tsx; layout/StaffLayout.tsx; views/{login,patient/login}/index.tsx; views/chat & patient/chat; views/patient/{layout/PatientLayout,dashboard,lab}/index.tsx; views/system/users/index.tsx |
| 6.2 | `SysUserVO.email` annotated `@PhiField` (no more plaintext PHI email in the Redis cache). Core PHI reads audited: `PatientService.getById` (VIEW), FHIR Patient/Observation reads (FHIR_VIEW), portal vitals/problems/immunizations/referrals/care-plans/prior-auths (ACCESS), patient history/allergy reads (VIEW_HISTORY/VIEW_ALLERGIES) — all `phiAccess=true` | module/system/dto/SysUserVO.java; module/patient/service/PatientService.java; module/patient/controller/{PatientController,FhirPatientController,FhirObservationController,PatientPortalController}.java |
| 6.3 | Pagination bounds: `PageQuery` gains `@Min(1)/@Max(200)` with `@Valid` on all 7 controller params; `GlobalExceptionHandler` maps `HandlerMethodValidationException` and `IllegalArgumentException` (e.g. `PageRequest.of(-1)`) to 400 instead of 500 | common/base/PageQuery.java; module/appointment/controller/ReferralController.java; module/billing/controller/{ChargeController,PriorAuthController}.java; module/patient/controller/{CarePlanController,ImmunizationController,ProblemController,VitalSignController}.java; common/exception/GlobalExceptionHandler.java |
| 6.4 | `AesAttributeConverter` refuses to re-encrypt the `[DECRYPT_FAILED]` placeholder (throws) — a decrypt failure can no longer permanently overwrite real ciphertext. Dead dependencies removed: Querydsl (declared but never used — Specifications are the ORM layer) and the unused knife4j version property; springdoc property aligned to 2.7.0; CLAUDE.md ORM row updated | common/config/AesAttributeConverter.java; pom.xml; CLAUDE.md |

## Verified

- `mvn clean compile` + `mvn test`: **161 tests, 0 failures**.
- `npx tsc --noEmit` clean; `npm run build` (Vite production) succeeds.

## Notes

- API-LAYOUT.md updated: `GET /api/v1/audit-logs/verify` added; prescription create/transmit/refill semantics documented (overrideReason, server-derived prescriber identity, `status: "generated"`, refill budget consumption).
- Known follow-ups (documented, not blocking): Okta JWKS vs local HS256 trust split verified by integration only; structured clinical PHI (observation values, vital signs) still plaintext at rest pending the eCQM-view decision; `patientInfo`-based header shows cached name before fresh profile loads; SSE emitter overwrite on multi-tab.
- **Full-System Review III: all 8 CRITICAL + 24 HIGH + 16 MEDIUM + 12 LOW addressed — verdict flips from Blocked to Ready-to-merge on the review findings (verification: 161 backend tests, tsc, prod build).**

---

# Fix Batch 7: C2 — AES Key Rotation Redesign (Review III C2) ✅ Complete (2026-08-20)

> Corrective round: Review III C2 (key rotation destroying versioned ciphertext) was **missed by the original Batch 2–6 plan** and flagged as still open in follow-up. This round closes it.

## Problem (as confirmed)

1. `AesCryptoUtil.decrypt` tried **only CURRENT_KEY** for v1-prefixed rows — after `rotate()` every existing row failed GCM auth and became `[DECRYPT_FAILED]`.
2. `KeyRotationService` migration predicate `NOT LIKE '01%'` excluded every v1 row — nothing was ever re-encrypted after rotation.
3. Even with the predicate fixed, `reencrypt()` returned null because `decrypt()` failed — fixing one spot alone was insufficient.

## Changes

| # | Change | Files |
|---|--------|-------|
| 7.1 | `decrypt()` now tries CURRENT_KEY first, then falls back to PREVIOUS_KEY for v1 rows (GCM auth failure ⇒ wrong key). Both keys share the static `0x01` version byte, so fallback is the only correct discrimination | common/config/AesCryptoUtil.java |
| 7.2 | New `AesCryptoUtil.isEncryptedWithPreviousKey(cipherHex)` — used by migration to target exactly the rows that need re-encryption (current-key rows are left untouched) | common/config/AesCryptoUtil.java |
| 7.3 | `KeyRotationService.migrateColumn` rewritten: full `ORDER BY id LIMIT/OFFSET` scan (no `NOT LIKE '01%'` exclusion), migrating only previous-key rows; `countLegacyRows` removed (progress = rows migrated) | common/job/KeyRotationService.java |
| 7.4 | `ENCRYPTED_COLUMNS` completed — every `@Convert(AesAttributeConverter)` column is now listed, including the Round 48/49 free-text fields (appointment, referral, allergy_entry, care_plan, immunization, medical_history_entry, problem, vital_sign, cds_override, refill_request, emergency_access, charge, prior_auth) and the Batch 4 prescription fields (diagnosis, icd10_codes, pharmacy_name, pharmacy_phone, drug_name, dosage, sig, notes) | common/job/KeyRotationService.java |
| 7.5 | Restart-consistency guard: `rotate()` records the new key's SHA-256 fingerprint in `key_audit` with an explicit "update AES_KEY/AES_KEY_PREVIOUS before restart" instruction; `init()` reads the latest KEY_ROTATION record and logs a loud ERROR when the configured `app.aes.key` fingerprint does not match (prevents silent post-restart unreadability) | common/config/AesCryptoUtil.java; common/audit/KeyAuditRepository.java |

## Tests

- New `rotation_shouldKeepVersionedRowsReadableAndMigratable` in `AesAttributeConverterTest` (4-phase): v1 row written under key A → rotate to B → old v1 row still decrypts via fallback; new writes use B; `isEncryptedWithPreviousKey` targets only old rows; `reencrypt` succeeds and the migrated row is current-key; restart simulation with `(B, A)` keeps both readable.
- `mvn test`: **162 tests, 0 failures** (161 prior + 1 new).

## Verified

- `mvn clean compile` + `mvn test` green.
- Manual trace: rotate(A→B) then decrypt of pre-rotation ciphertext returns plaintext; migration re-encrypts only previous-key rows; stale-config restart logs the KEY ROTATION CONFIG MISMATCH error.

## Operational note

- Runtime rotation via `POST /api/v1/admin/keys/rotate` requires the operator to update `AES_KEY` (new) and `AES_KEY_PREVIOUS` (old) **before restart** — the fingerprint guard now detects a missed update instead of silently corrupting. Preferred flow: update env first, restart (init picks up previous-key + rotation auto-runs via `startIfNeeded`), then monitor `/rotation-status` until complete.

---

# Round 50: Maintainability Pass ⬜ Planned (2026-09-11)

> Independent code-quality review (backend 212 main + 7 test Java files / 13,916 LOC main + 2,782 LOC test; frontend 81 TS/TSX / 6,360 LOC + 6 CSS / 175 LOC; schema, pom and all config). **Ranked for code quality and long-term maintainability, not compliance.**
>
> **Scope decision (user, 2026-09-11): H2-only learning demo.** DB migration tooling is **out of scope** — no Flyway/Liquibase, no MySQL schema-evolution work, no prod deployment hardening. The review's "no migration mechanism" finding is withdrawn on that basis; only the H2-file-staleness footgun it implies survives (F14/M1).
>
> Status: **M1–M7, M9, M10 ✅ complete (2026-09-11 → 2026-09-15); M8 🟠 in progress (M8.1–M8.3 ✅ 2026-09-16, M8.4–M8.6 ⬜).** M10 was pulled ahead of M4–M9 because it is a functional defect, not cleanup. Each batch below flips to ✅ individually when it lands.

## Summary

The project's *reviewed* surfaces are in good shape: 26/26 `BaseEntity` subclasses carry `@SQLDelete` + `@SQLRestriction` + `@Version` (the 7 reference-table exceptions match CLAUDE.md exactly), all 55 `@Convert(converter = AesAttributeConverter.class)` annotations are applied consistently, and DTO factories are used throughout. What this round targets is the **uncovered systematic dimension** the 49 previous rounds never swept: duplicated implementations that drift, magic-value domain state, silent failure paths, unreviewed test ergonomics, and the total absence of mechanical guards (no lint, no CI, no static analysis, no `@Size` anywhere). These cannot be closed by another manual review round — they need extraction and tooling.

## Findings (review input)

| ID | Sev | Finding | Key evidence | Batch |
|----|-----|---------|--------------|-------|
| F1 | 🟡 | UI CSV export bypasses the backend streaming export entirely — two implementations, already diverged (rate limit, audit trail, PHI masking, formula-injection guard all lost on the path the UI actually uses) | `medical-web/src/layout/StaffLayout.tsx:5,91,94` → `api/export.ts:20,34` (`?page=1&size=9999`) vs `module/export/controller/ExportController.java:36,82` + `common/config/RateLimiterConfig.java:101` (`/api/v1/export/*`) | M2 |
| F2 | 🟡 | Refresh failure leaves queued requests **hanging forever** (neither resolved nor rejected) | `medical-web/src/api/request.ts` + `patientRequest.ts` — failure branch does `refreshSubscribers = []`, dropping the pending `subscribeTokenRefresh` callbacks | M3 |
| F3 | 🟡 | Test suite is a single order-dependent class with shared static state; no test can run standalone or in parallel, and the crypto test mutates a JVM-global key | `src/test/.../IntegrationTest.java` (2,109 lines, 128 tests, `@TestMethodOrder(OrderAnnotation)`, `static adminToken/doctorToken/patientToken` at :55-57, `@Sql` only `BEFORE_TEST_CLASS`); `AesAttributeConverterTest` → `AesCryptoUtil.initializeForTest` | M4 |
| F4 | 🟠 | Domain status is bare `int`/`String` everywhere (no enums in the codebase), and that has already produced 4 real defects | status 6 does not exist + no-show(4)→`CANCELLED` in `PatientCaseService.buildEncounter`; dead/incorrect `ethnicityToOmbCode` ordering (`contains("hispanic")` before `contains("not hispanic")`); `LANGUAGE_EXT_URL` (:46) points at `us-core-birthsex` and is used for preferred language (:208); bare `orElseThrow()` in `ChargeService.convert:61` → 500 not 404 | M5 |
| F5 | 🟠 | `request.ts` / `patientRequest.ts` are ~95% copy-paste (refresh queue, proactive refresh, `http` facade) and have already drifted (blob handling exists in one only) | `medical-web/src/api/request.ts` (140 lines) vs `api/patientRequest.ts` (122 lines) | M3 |
| F6 | 🟠 | Systematic silent-failure paths: encryption failure stores `NULL` PHI, and a catch-all handler disguises programming errors as client errors | `AesCryptoUtil.encrypt:141-144` returns `null` on failure; `GlobalExceptionHandler:68-72` maps **every** `IllegalArgumentException` to 400 without logging; 7 backend `catch (Exception ignored)`, 20 frontend `catch {}` | M6 |
| F7 | 🟠 | 11+ endpoints return raw JPA entities (against CLAUDE.md §4); one endpoint takes `Map<String,Object>` with unchecked casts and no validation; TS types are a superset of the real contract | `PatientPortalController:314-359`, `ConsentController:31,64`, `QualityController:23,39`, `PharmacyController:21`, `FormularyController:40`; `PatientPortalController:107-127`; `types/entities.ts:886` declares `accessToken/expiresIn/user` that `LoginResponse.java` never sends | M8 |
| F8 | 🟠 | Two coexisting pagination mechanisms: 7 endpoints use validated `PageQuery` (≤200), 19 raw `@RequestParam page/size` sites in 11 files have **no upper bound at all** | `common/base/PageQuery.java` vs `PatientController:51`, `BillController:31`, `PrescriptionController`, `AuditLogController`, …; `size=9999` accepted today | M7 |
| F9 | 🟠 | 4 module dependency cycles (`patient↔system`, `↔appointment`, `↔prescription`, `↔billing`) and `common → module` reverse dependencies; the epicentre is an 18-dependency controller | `PatientPortalController` (18 injected deps, business logic + `@Transactional` in the controller); `patient/dto/PatientDataExport.java` importing 3 modules; `common/security/DoctorPatientScope`, `common/job/*` importing module repositories | M8 |
| F10 | 🟠 | Crypto utility is a static global holder wired by a separate bean's `@PostConstruct`, with a production-visible test backdoor, and derives an audit fingerprint by parsing its own log text | `AesCryptoUtil` `static CURRENT_KEY/PREVIOUS_KEY/rotationActive/keyAuditRepo`; `KeyAuditBridge`; `initializeForTest`; `warnIfStaleConfigAfterRuntimeRotation:98-118` (`substring`/`indexOf` on a human-readable `detail`) | M6 |
| F11 | 🟠 | Encrypted-column capacity is silently halved (hex = `2 × (plaintext + 29)`) while schema/entity widths claim the full size, and there is **no `@Size` anywhere** (0 occurrences) | `resources/sql/schema.sql` `medical_history VARCHAR(4000)` ≈ 1,971 chars usable, `allergies VARCHAR(2000)` ≈ 971, `name VARCHAR(200)` ≈ 71; `Patient.java:122,126` `@Column(length=4000/2000)` | M7 |
| F12 | ⚪ | Dead code, dead config, doc drift and missing mechanical guards | dead: `util/CsvUtil` (0 callers, and it lacks the formula-injection guard `ExportController.csv` has), `tokenStore.clearAll()` (0 callers while logout hand-removes 9 keys), empty `com/martin/medical/` dir, unused `springdoc.version` property (`pom.xml:25` vs hardcoded `2.7.0` at :92), `hutool` pulled in for 3 `StrUtil.isBlank`; no ESLint/CI/`typecheck` script, `@types/react@19` against `react@18.3.0`; doc drift: CLAUDE.md claims `patientRequest` does **not** unwrap (it does, `patientRequest.ts:35`), API-LAYOUT.md:222 grants `DOCTOR` on `PUT /bills/{id}/pay` (code is `hasRole('ADMIN')`), API-LAYOUT.md:42 still lists dead `CsvUtil` | M9 |
| F13 | 🟡 | **The documented H2 quick start cannot boot without Redis** — README says `h2 = local file DB, no external dependencies`, but the h2 profile keeps the rate limiters enabled, so `RedissonClient` connects eagerly and aborts startup | verified by booting `SPRING_PROFILES_ACTIVE=h2` against an unreachable Redis: `Error creating bean 'loginRateLimiter' … 'redisson' … RedisConnectionException`; `application-h2.yml` sets only `app.rate-limit.export-per-hour: 100`, while `application-dev.yml` sets `rate-limit.enabled: false` (so **dev** boots without Redis and **h2** does not) | M1 |
| F15 | 🟠 | **Configured rate limits are silently ignored once Redis holds limiter state.** Redisson's `trySetRate` only initialises a limiter that does not exist yet, so `RateLimiterConfig`'s per-request `trySetRate` is a no-op on an existing key — and the remaining-permit counter / sliding window (`{key}:value`, `{key}:permits`) outlive the config hash, so an old budget keeps applying. Also: the 429 message hardcodes "Max 5 exports per hour" while the limit is `app.rate-limit.export-per-hour` (100 in h2) | discovered verifying M2: with `export-per-hour=3` and a stale `value=82` (left from the earlier 100/hour config) **21 consecutive exports all returned 200**; after deleting all three keys the 4th returned 429. Control: the login limiter (10/min) refused at #7 on the same instance | M10 |
| F14 | 🟠 | H2 file DB is never versioned, and schema drift fails silently instead of loudly | `schema.sql` is 37/37 `CREATE TABLE IF NOT EXISTS` with **zero** ALTER/DROP/ADD; combined with a persistent `~/.medical-dev/data/medical_dev` + `DataInitializer` early-return on `COUNT(*)>0`, a schema change leaves missing columns that only surface on first touch (this is the already-documented `audit_log.prev_hash` incident, header block 2026-08-20); README documents the path but not the reset step | M1 |

## Non-goals (explicitly out of scope this round)

- **DB migration tooling / schema evolution** (user decision) — no Flyway, no Liquibase, no MySQL work. F14 is addressed only as H2-file hygiene + documentation.
- Prod deployment hardening, `SecurityConfigProd`/`ProdGuard`/`CompositeJwtDecoder` redesign — unreachable under H2-only and left as-is (they remain useful learning material; note they are the least-exercised code in the repo).
- Any new runtime dependency. M9 only *removes* one (`hutool`).
- Rewriting the 49 completed rounds' accepted trade-offs (key rotation design, audit hash chaining, EPCS fail-closed).

## Execution order

Batches are independent except where noted; each is small enough to land and verify on its own.

| Order | Batch | Findings | Depends on |
|-------|-------|----------|------------|
| 1 | M1 — H2 quick-start correctness | F13, F14 | — |
| 2 | M2 — Export path unification | F1 | M7 (only for the `size=9999` bound) |
| 3 | M3 — API client consolidation + auth contract | F2, F5 | — |
| 4 | M4 — Test-suite decomposability | F3 | — |
| 5 | M5 — Domain status enums + mapping fixes | F4 | M8 (if VOs change status types, coordinate) |
| 6 | M6 — Crypto & error-handling contract | F6, F10 | M4 (test isolation first, since M6 changes static state) |
| 7 | M7 — Pagination unification + input bounds | F8, F11 | M3 (frontend `size` call sites) |
| 8 | M8 — Layering: VO/DTO extraction + controller split | F7, F9 | M5, M7 |
| 9 | M9 — Tooling guardrails, dead code, doc sync | F12 | after M2/M3/M7 (tooling then guards the new shape) |
| 10 | M10 — Rate limiter config actually applies | F15 | — |

## Data Contract Trace

Every batch that touches a request or response payload, traced field-by-field (CLAUDE.md Plan rule: never assume empty defaults are safe).

| Batch | Contract | Fields / source of truth | Notes |
|-------|----------|--------------------------|-------|
| M2 | `GET /api/v1/export/patients`, `/export/bills` → `Blob` + `Content-Disposition` | No request body. Response is `text/csv; charset=UTF-8`; the UI currently never calls it. Filename must come from the header, not be hardcoded | `request.ts` already special-cases `responseType === 'blob'`; reuse that path. Errors arrive as a JSON body inside a Blob — must parse it to surface 429/403 messages |
| M3 | `POST /auth/refresh` `{refreshToken}` → `LoginResponse{token, refreshToken, userId, username, realName, roles, permissions}`; `POST /patient/refresh` `{refreshToken}` → `PatientLoginResponse{token, refreshToken, patientId, name, username}` | Both responses use **`token`**, never `accessToken`. The patient refresh path returns `patientId`/`name` as `null` (`PatientAuthController` refresh branch) — the client must not depend on them | Fix `types/entities.ts:886` (drop fictitious `accessToken`/`expiresIn`/`user`) and the `accessToken \|\| token` guess in `request.ts`. Correct CLAUDE.md's "does NOT unwrap" claim — patient views must use the unwrapped value (`patientRequest.ts:35`) |
| M5 | Appointment `status` stays `INT` in the DB | enum is the single source of truth: `0 SCHEDULED, 1 ARRIVED, 2 CANCELLED, 3 COMPLETED, 4 NO_SHOW` (4 confirmed by `AppointmentScheduler:46`; 2 by `AppointmentRepository` JPQL `status <> 2`) | If `AppointmentVO.status` changes from `Integer` to an enum name, the only frontend numeric coupling is the form default (`views/appointments/index.tsx:11 status: 0`) — audit that plus `types/entities.ts` `AppointmentVO/AppointmentForm` before changing the wire type |
| M8 | `PUT /api/v1/patient/me` body | Backend allow-list (12 editable fields, `PatientPortalController:112-123`): `phoneMobile, phoneHome, phoneWork, email, addressLine1, addressLine2, city, state, zipCode, emergencyContactName, emergencyContactPhone, emergencyContactRelation`. The UI (`views/patient/profile/index.tsx:44,73`) sends the **whole** `PatientProfileVO` (incl. readonly `name/mrn/dateOfBirth/sexAtBirth/insurancePayer/allergies`) and the backend silently ignores the extras | New `PatientSelfUpdateFormDTO` must contain exactly those 12 + `@Size`; the frontend must send only `FIELDS.filter(f => !f.readonly)`. `PUT /patient/me/password` sends `{oldPassword, newPassword, confirmPassword}` while the DTO reads only the first two — decide and document (strip client-side or accept+verify server-side) |
| M7 | `GET /patients`, `/bills`, `/appointments`, `/prescriptions`, `/audit-logs`, … `?page&size` | Adding a bound changes behaviour: `size > 200` → 400. Callers to fix first: `api/export.ts` (disappears in M2), `views/lab/LabResults.tsx:23` (`size: 999`), `views/referrals/index.tsx:30` (`200`), `views/patients/index.tsx` (`100` ×5) | `PAGE_SIZE = 10` (`utils/labels.ts:45`) is the norm. CLAUDE.md's "`size: 999`" guidance must change to the real cap |

## Docs to Update

| Doc | Change | Batch |
|-----|--------|-------|
| `README.md` | ✅ M1: prerequisites, "h2 needs no Redis", H2 reset procedure, `DevSchemaGuard` / `SCHEMA_VERSION` bump rule, `H2_DB_PATH` examples, rate-limit re-enable steps | M1 |
| `docs/API-LAYOUT.md` | :222 `PUT /bills/{id}/pay` role ADMIN (not ADMIN,DOCTOR); :42 remove dead `CsvUtil`; M7 records the new `size` bound as a breaking behaviour change; M8 records VO/`PatientSelfUpdateFormDTO` payloads | M7, M8, M9 |
| `CLAUDE.md` | ✅ M1: test row updated 162 → 166 (128 integration + 38 unit). ✅ M3: the `patientRequest` unwrapping claim is corrected and the shared-client rule documented. Still open: update the `size: 999` pagination guidance to the real cap (M7); refresh the ORM/test rows after M9 tooling lands | M1, M3, M7, M9 |
| `docs/ROADMAP.md` | This section (round summary + files changed + verification per batch, per CLAUDE.md §11) | each batch |
| `docs/backend-architecture-explained.md` | Refresh the layer diagram after M8 removes the `common → module` edges and splits the portal controller | M8 |

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| M3 merges two clients that have 28 `api/*` importers + 18 patient-view importers | Keep both public entry points (`request` default, `patientRequest` default, `http`) and change only the internals; the factory is internal. `npx tsc --noEmit` is the safety net |
| M4 removing `@Order` may surface pre-existing hidden coupling (tests that only passed because an earlier test seeded state) | Land the cleanup and the isolation in the same batch and fix fallout there — a test that cannot run alone was not measuring anything |
| M4/M6 changing `AesCryptoUtil` static state can invalidate the existing `AesAttributeConverterTest` fixtures | Keep the static bridge (JPA instantiates converters outside Spring — this cannot be injected away) but make the test restore the previous key in `@AfterEach` and add an explicit "key state" assertion; do not let a test leave a global key behind |
| M5 enum migration touches DB values, seeds, VOs and the frontend | Keep the DB column `INT` and introduce the enum as a code-level single source of truth first (no schema/seed change); a full `@Enumerated` migration is a follow-up only if it earns its keep |
| M6 making `encrypt()` throw changes failure semantics from "silently null PHI" to "fail the write" | Intentional: a failed PHI write must be loud. Consistent with `AesAttributeConverter` already throwing on `[DECRYPT_FAILED]`. Document it as a behaviour change |
| M7 adding `@Max(200)` breaks any client sending a larger size | Deliberate, documented breaking change; fix the 3 known call sites in the same batch (`api/export.ts` is gone by M2) |
| M8 is the largest batch (layer reshuffle) and touches the portal + FHIR export paths | Split by sub-item: VOs first (mechanical, low risk), then the `Map` → DTO swap, then the controller split; each sub-item independently verifiable |
| Scope creep into prod/compliance work (already excluded by the user) | Non-goals above are binding; any batch that needs a schema change or a prod-path change stops and asks |

## Batch M1 — H2 quick-start correctness 🟡 ✅ Complete (2026-09-11)

**Goal:** the documented H2 quick start works on a clean machine, and schema drift fails loudly instead of silently.

> Investigated first: disabling the limiters is **not** sufficient. With `app.rate-limit.enabled=false` and an unreachable Redis the context still dies at `redisTemplate → redissonConnectionFactory → redisson` (`RedissonAutoConfigurationV2` builds its client eagerly). Excluding that auto-configuration is what actually makes h2 dependency-free.

### Changes

| # | Change | Files |
|---|--------|-------|
| M1.1 | h2 is now genuinely zero-dependency: `app.rate-limit.enabled: false` **plus** `spring.autoconfigure.exclude: org.redisson.spring.starter.RedissonAutoConfigurationV2` (Boot's own Lettuce config stays and connects lazily). Both commented in place with the reason, since "just disable the flag" looks sufficient but is not | `resources/application-h2.yml` |
| M1.2 | README Quick Start rewritten: real prerequisites (JDK 17+ / Maven / Node 18+), explicit "h2 needs no Redis", new **Local database (h2 profile)** section (reset procedure + `DevSchemaGuard` behaviour + `H2_DB_PATH` examples) and **Rate limiting** section (how to re-enable, actual limits per endpoint) | `README.md` |
| M1.3 | New `DevSchemaGuard` (`@Profile({"dev","h2"})`, `@Order(HIGHEST_PRECEDENCE)`): records the schema version the file was built with and refuses to start on a mismatch. Backed by a new `schema_version` table plus a header block in `schema.sql` explaining that `IF NOT EXISTS` + `mode: always` never alters an existing file, and the encrypted-column capacity rule. README documents "bump `SCHEMA_VERSION` in the same commit as a schema change" | `common/config/DevSchemaGuard.java` (new); `resources/sql/schema.sql`; `README.md` |
| M1.4 | Tests for the guard (fresh DB records baseline / matching version is idempotent / older schema fails fast without stamping the DB / newer schema fails) — runs the real `sql/schema.sql`, so it also proves that file defines `schema_version` | `src/test/.../config/DevSchemaGuardTest.java` (new) |

### Verification

- **No Redis, committed config (only ports overridden), temp file DB:** `Started MedicalApplication in 7.388 seconds` + `Deployment guard passed for profile(s): h2` + `DevSchemaGuard - Local schema version recorded: v1`. Before this batch the same command aborted with `RedisConnectionException`.
- **Drift path end-to-end:** bumped `SCHEMA_VERSION` to 2 against the existing v1 database → boot aborts with exit code 1 and `IllegalStateException: Local database schema is v1 but this build expects v2 (jdbc:h2:file:…). spring.sql.init never alters existing tables, so the new columns are missing. Delete the local database file (default: ~/.medical-dev/data/, or the path in H2_DB_PATH)…` (version reverted to 1 afterwards).
- `cd medical-server && mvn test` → **166 tests, 0 failures** (162 prior + 4 new). The new test caught a real bug during development: H2's `rs.getInt()` maps SQL `NULL` to `0`, so a *fresh* database was rejected as "version 0" — the guard now reads `getObject()`.
- Debug pass on the in-mem check DB deliberately skipped: the app binds no port and touches no datasource (fails during runner phase), so no local data is mutated.
- **Transition from an existing database verified** against a copy of the real `~/.medical-dev/data/medical_dev.mv.db`: it boots, gets the (new) `schema_version` table stamped v1, and seeds normally. That copy turned out to be **schema-only with 0 rows** in `sys_user`/`patient`/`appointment` (37 tables, no data) — a separate pre-existing observation: the local demo DB had never completed a seed, so `admin`/`doctor1`/`patient1` could not log in.
- **Local DB reseeded as an ops follow-up (2026-09-11):** stale empty file deleted, fresh boot rebuilt schema + seed — `schema_version=1`, 2 `sys_user` (admin, doctor1), 4 patients / 4 `patient_auth` logins, 3 roles, 9 menus, 5 appointments, 3 prescriptions, 3 bills, 74 observations, 30 LOINC entries. Login verified end-to-end against a byte-identical copy (no Redis): `admin/admin123`, `doctor1/doctor123`, `patient1/patient123` → all HTTP 200 with a JWT.
- `H2_DB_PATH` takes a **plain path**, not a URL (`jdbc:h2:file:` is already in the template). The old comment suggesting `file:./data/medical_dev` produced `jdbc:h2:file:file:./data/...` — H2 tolerated it, but both the YAML comment and the README example now use the plain-path form.

### Notes

- Behaviour change: local h2 no longer depends on Redis, and rate limiting is off there by default. Redis enforcement is unchanged in `dev`/`prod`.
- The guard runs as a `CommandLineRunner`, so it fails **after** the context refresh (`Started MedicalApplication` appears just before the abort) rather than before Tomcat binds. Deliberate: running earlier would require depending on Boot's internal `dataSourceScriptDatabaseInitializer` bean name, which is more brittle than the cosmetic log ordering it would fix. Non-zero exit and a clear message are preserved either way.
- `dev` still requires Redis at boot even though it too sets `rate-limit.enabled: false` (same eager-Redisson mechanism, no exclusion there). Left as-is deliberately — `dev` is the MySQL profile; one-line follow-up if that ever matters: add the same exclusion to `application-dev.yml`.
- The guard's baseline is v1 *now*: a pre-existing local file that predates this batch gets the (empty) `schema_version` table created and stamped v1 on first boot, so it cannot detect drift that already happened. Recommend a one-time reset for a clean baseline.

## Batch M2 — Export path unification 🟡 ✅ Complete (2026-09-11)

**Goal:** one CSV export implementation, the one that already exists on the server, so rate limiting, the audit trail and PHI masking apply to what the user actually clicks.

> The backend export endpoints were verified **before** routing the UI to them (they had never been exercised — the UI only ever called `/patients?size=9999`): correct `Content-Disposition`, masked PHI, audit rows written. Routing the UI to them was then a small change.

### Changes

| # | Change | Files |
|---|--------|-------|
| M2.1 | `downloadPatientsCsv`/`downloadBillsCsv` now fetch `/export/patients` and `/export/bills` as blobs and save them under the **server's** filename; the client-side row building, the second `csv()` escaper and the `size=9999` requests are gone (48 → 25 lines) | `medical-web/src/api/export.ts` |
| M2.2 | Blob responses now resolve to `{ blob, filename }` (`BlobDownload`) instead of a bare Blob, so callers never hardcode a name the backend owns. The dead `if (res.status < 400)` branch inside the *fulfilled* handler is removed (axios routes every non-2xx to the rejected handler) | `medical-web/src/api/request.ts` |
| M2.3 | Failed downloads surface the backend's message: a JSON error body arrives **inside a Blob**, so `err.response.data.message` was undefined and every failure collapsed to the axios default text. New `serverErrorMessage()` decodes it, and the server message is now checked **before** the generic 429 text | `medical-web/src/api/request.ts` |
| M2.4 | Export failures show the real reason instead of a bare `alert('Export failed')` | `medical-web/src/layout/StaffLayout.tsx:91,94` |

### Verification

- Backend contract (curl, before wiring): `HTTP 200`, `Content-Disposition: attachment; filename=patients.csv`, `Content-Type: text/csv;charset=UTF-8`; patients CSV = 4 rows with `Phone=****0101`, `Email=j***@email.com`; bills CSV masks claim numbers (`****0001`).
- Audit trail: each download writes `EXPORT_PATIENTS` / `EXPORT_BILLS` (module `export`, ip recorded) — this never happened on the old client-side path.
- `filenameFromDisposition` unit-checked against 7 inputs: plain, quoted, RFC 5987 `filename*=UTF-8''…`, `inline`, `undefined`, a number, and a trailing parameter → `patients.csv` / `bills.csv` / null as appropriate.
- `serverErrorMessage` unit-checked with the **exact** 429 body the filter emits: Blob(JSON) → the message, Blob(HTML) → undefined, plain object → the message, undefined → undefined.
- Export rate limit verified end-to-end against a second instance (`export-per-hour=3`, limiters on): 3× `200` then 3× `429` with `{"code":429,"message":"Export rate limit exceeded. Max 5 exports per hour."}` — the text the UI now displays.
- Vite serves the rewritten module (`/export/patients`, `responseType: "blob"`); `npx tsc --noEmit` clean; `npm run build` clean (213 modules).
- **Not machine-verified:** the last browser hop (blob → file saved to disk). No browser driver is available in this environment — needs one click in the running app.

### Notes

- Behaviour change: the export button now depends on the backend being up. It also goes through the export rate limiter, so a user can legitimately be refused (and now sees why).
- The old client-side export wrote a UTF-8 BOM while the server does not; the download still prepends one (a client-side `new Blob([BOM, blob])`) so Excel keeps opening the file correctly.
- New finding **F15** (see the findings table) came out of verifying the 429 path — the configured export limit was silently ignored because of stale Redis state. Recorded as batch M10.

## Batch M3 — API client consolidation + auth contract 🟡 ✅ Complete (2026-09-11)

**Goal:** one HTTP client implementation, no request can hang forever, and the auth types match what the server sends.

### Changes

| # | Change | Files |
|---|--------|-------|
| M3.1 | New `createApiClient({tokenKey, refreshTokenKey, refreshUrl, loginPath})` holds everything the two clients had duplicated: token injection, 401 → single-flight refresh, proactive refresh, the blob branch, error mapping and the `http` facade. `request`/`patientRequest` became 14/12-line instances, so all 45 importing files are untouched. The `declare module 'axios'` augmentation (`_retry`/`silent`) now exists once | `api/createClient.ts` (new); `api/request.ts`; `api/patientRequest.ts` |
| M3.2 | **Hang fixed:** waiters are `{resolve, reject}` pairs and a failed refresh **rejects every parked request** (`settleWaiters`), instead of `refreshSubscribers = []` discarding them — those promises never settled, so the caller's spinner span forever | `api/createClient.ts` |
| M3.3 | Refresh reads `data.token` only (no `accessToken \|\| token` guessing) and **fails loudly when no token comes back** rather than storing `''`. Refresh-token reads go through `tokenStore` instead of a mixed `localStorage`/`sessionStorage` access | `api/createClient.ts` |
| M3.4 | `LoginResponse` corrected to the real payload — dropped the invented `accessToken`, `expiresIn` and `user` fields (nothing read them; the type was a superset of the API) | `types/entities.ts` |
| M3.5 | Corrected CLAUDE.md's client guidance: it claimed `patientRequest` does **not** unwrap and to keep `r.data.data.x` — the interceptor unwraps, so following that instruction yields `undefined`. New text documents both instances of the factory and where shared behaviour belongs | `CLAUDE.md` |
| M3.6 | Bonus fix in the same code path: the proactive timer now has a **5 s floor**. `scheduleDelayMs` returns 0 for a token without `exp` (or an expired one), and the old code re-armed immediately — a 0 ms refresh loop against a failing endpoint | `api/createClient.ts` |

### Verification

- **Executed the real module** (esbuild CJS bundle, Node fake server returning 401 + refresh outcomes — a throwaway harness, not committed):

  | Case | Result |
  |------|--------|
  | two concurrent 401s, refresh **succeeds** | both fulfilled, **exactly 1** refresh call, rotated refresh token stored |
  | two concurrent 401s, refresh **fails** | **both rejected** (this is the old hang), 1 refresh call, tokens cleared, redirected to `/login` |
  | no refresh token | rejected, **0** refresh calls, redirected |
  | `silent: true` | rejected **without** redirect |
  | token with no `exp`, 11 s elapsed | **2** proactive refreshes (5 s floor) — without the floor this is a 0 ms loop |

- `npx tsc --noEmit` clean with the corrected `LoginResponse` (no `accessToken`/`expiresIn`/`user` usage remains anywhere); `npm run build` clean, bundle 435.28 → **433.41 kB** (the deduplication).
- `request.ts` + `patientRequest.ts`: 262 → 26 lines combined; no old symbols (`refreshSubscribers`, `subscribeRefresh`, duplicated interceptors) left outside the factory.

### Notes

- Still not machine-verified: a real browser refresh cycle (the harness drives the same code but not a real login → token expiry → refresh → retry). Logging in, waiting out the access-token TTL and clicking around covers it.
- The emergency-token raw-axios calls in `views/patients/index.tsx:107-111` remain a legitimate second credential; a third instance would cover them if that path grows.
- The two login views still use bare `axios` (they run before a token exists) — that is now the only place `r.data.data` is correct, and CLAUDE.md says so.

## Batch M4 — Test-suite decomposability 🟡 ✅ Complete (2026-09-14)

**Goal:** any single test can be run on its own, class execution order stops mattering, and no test leaves process-wide state behind for the rest of the JVM.

### Changes

| # | Change | Files |
|---|--------|-------|
| M4.1 | The 2,109-line / 128-test `IntegrationTest` monolith is split into 17 classes along its own section seams (auth, system, profile, patient, appointment, prescription, billing, chat, dashboard, portal, FHIR, export, audit log, Mirth, lab, quality, emergency access) plus `IntegrationTestSupport` holding the harness (annotations, `MockMvc`/`ObjectMapper`, login helpers). Largest class is now 523 lines | `src/test/java/com/example/medical/{IntegrationTest.java → 17 classes + IntegrationTestSupport.java}` |
| M4.2 | The suite-wide `static adminToken/doctorToken/patientToken` are gone. Each class logs in what it needs in its own `@BeforeAll` (with `@TestInstance(PER_CLASS)`), so no test depends on whichever test happened to run first | the 17 classes |
| M4.3 | Cleanup stays per class: `@Sql(cleanup-test-data.sql, BEFORE_TEST_CLASS)` moved to the base class, so every class starts from the seeded state instead of inheriting the previous class's writes | `IntegrationTestSupport.java` |
| M4.4 | **Fixed the process-wide AES key leak**: `AesAttributeConverterTest` swapped the JVM-global key via `initializeForTest`/`rotate` and never put it back, so any integration class running later in the same JVM decrypted seeded rows to `[DECRYPT_FAILED]` and then 500'd on the next save (`AesAttributeConverter` refuses to persist the placeholder) | `common/config/AesCryptoUtil.java` (snapshot/restore seam); `common/config/AesAttributeConverterTest.java` (`@BeforeEach` snapshot → `@AfterEach` restore) |
| M4.5 | Five classes keep an explicit **class-local** `@TestMethodOrder` because their tests are a deliberate sequence (create → update → delete, or a claim lifecycle: create → submit → adjudicate → pay); the other 12 are order-free. Ordering is documented in each class's javadoc, and remains class-local: the class still runs standalone | `Appointment/…, Billing/…, EmergencyPatient…, Patient…, SystemIntegrationTest.java` |

### Verification

- `mvn test`: **166 tests, 0 failures** (same 128 integration + 38 unit as before the split — nothing lost or duplicated); total time **17.6 s** (the 17 classes share one Spring context).
- **Reverse class order** (`-Dsurefire.runOrder=reversealphabetical`): **166 tests, 0 failures**, with a genuinely different execution order. In that order `AesAttributeConverterTest` runs *between* integration classes — the exact case that failed before M4.4.
- Standalone method: `mvn test -Dtest='AuthIntegrationTest#login_withWrongPassword_shouldReturn401'` → 1 test, 0 failures.
- Standalone class: `mvn test -Dtest=BillingIntegrationTest` → 11 tests, 0 failures (an ordered class, run with no other class in the JVM).
- Crypto leak regression: `-Dtest='AesAttributeConverterTest,PatientIntegrationTest' -Dsurefire.runOrder=reversealphabetical` (crypto first, integration second, same JVM) → 24 tests, 0 failures.
- No unused imports introduced by the mechanical split (checked across all 17 files).

### Notes

- **The split found a real defect, not just an ergonomics problem.** The old single class happened to run `AesAttributeConverterTest` before the Spring context existed, so `init()` reset the key and the leak never showed. Splitting into 17 classes put unit and integration classes in the same JVM run in an order where it did show — every class after it failed. This is what F3 was about: the suite could not be measured properly while one class owned all of it.
- Two edge cases in the new seam were caught by verification rather than by review: restoring a snapshot taken *before* any Spring context exists (no key configured yet) must be a no-op, and a pointless 310k-iteration re-derivation is skipped when the key state is unchanged.
- Deliberate trade-off: making the five narrative classes fully self-contained (each test creating its own fixture, no ordering) is a larger rewrite than the ordering it removes. Class-local `@Order` is explicit and survives being run alone; note it as a follow-up if those sequences ever need to run in parallel.
- `IntegrationTestSupport` documents the per-class login pattern, so new tests do not reintroduce a suite-wide token.

## Batch M5 — Domain status enums + mapping fixes 🟠 ✅ Complete (2026-09-14)

**Goal:** appointment status stops being an unnamed integer, and the four confirmed mapping defects are fixed.

> The enum was derived from the code, not invented: only NO_SHOW (4, set by `AppointmentScheduler`) and CANCELLED (2, excluded by the conflict query) were documented anywhere. The DB column stays `INT` and the wire format stays numeric, so nothing changes for the frontend or the schema.

### Changes

| # | Change | Files |
|---|--------|-------|
| M5.1 | New `AppointmentStatus` (SCHEDULED 0, ARRIVED 1, CANCELLED 2, COMPLETED 3, NO_SHOW 4) with `code()`, `matches()`, `anyOf()`, `isTerminal()`, `fromCode()`. Replaces every magic number: `Set.of(2,3,4)` → `isTerminal()`, `Set.of(3,4)` → `anyOf(COMPLETED, NO_SHOW)`, `Integer.valueOf(3).equals(...)` → `COMPLETED.matches(...)`, `setStatus(4)` → `NO_SHOW.code()`, `setStatus(0)` default → `SCHEDULED.code()`, and the JPQL `status <> 2` → a named `:cancelledStatus` parameter fed from `CANCELLED.code()` | entity/AppointmentStatus.java (new); service/AppointmentService.java; repository/AppointmentRepository.java; common/job/AppointmentScheduler.java; controller/PatientPortalController.java; dto/AppointmentFormDTO.java |
| M5.2 | `buildEncounter` no longer invents outcomes: the nonexistent `case 6` is gone, and a cancelled or **no-show** visit now emits **no Encounter at all** instead of `CANCELLED` (FHIR has no no-show status; a visit that never happened should not be reported as a cancelled one). A null/missing status maps to `UNKNOWN` rather than throwing | module/patient/service/PatientCaseService.java |
| M5.3 | `ethnicityToOmbCode` tested the negative form second, so "Not Hispanic or Latino" matched `contains("hispanic")` and returned the **Hispanic** code; the second branch was dead, and its `contains("not")` would have matched almost anything. Negative forms are now checked first | same |
| M5.4 | Preferred language moved where FHIR R4 actually puts it — `Patient.communication.language` with `preferred: true`. The old code attached it as a `StringType` extension using a constant named `LANGUAGE_EXT_URL` that pointed at `us-core-birthsex` | same |
| M5.5 | The only bare `orElseThrow()` in the codebase → 404 `BusinessException` (was a 500 `NoSuchElementException`) | module/billing/service/ChargeService.java |
| M5.6 | `toLowerCase()` → `toLowerCase(Locale.ROOT)` in both OMB mappings (a Turkish default locale would otherwise change the match) | same |

### Verification

- `mvn clean test`: **166 tests, 0 failures**.
- **Live against the seeded data** (patient 100 has appointments 200/201 `COMPLETED` and 202/203 `NO_SHOW`), via `GET /api/v1/patients/100/case`:

  | Check | Before | After |
  |-------|--------|-------|
  | Encounter entries for patient 100 | 4, the two no-shows as `cancelled` | **2, both `finished`** — the no-shows emit nothing |
  | `Not Hispanic or Latino` (patient 100) | `2135-2` (Hispanic) | **`2186-5`** |
  | `Hispanic or Latino` (patient 101) | `2135-2` | `2135-2` (unchanged) |
  | Preferred language | `us-core-birthsex` extension | **`Patient.communication: [{language:{text:"en"}, preferred:true}]`** |
  | `PUT /api/v1/charges/999999/convert` | 500 | **404 `{"code":404,"message":"Charge not found"}`** |
  | `PUT /api/v1/charges/{existing}/convert` (happy path) | 200 | 200 (unchanged) |

- The parameterised conflict query is covered by `createAppointment_conflicting_shouldReturn409`, which stays green.

### Notes

- Deliberately **not** done here, to keep the batch reviewable: `sys_user.status` (0 disabled / 1 enabled) is still compared as a literal in `AuthService`, `SysUserService` and `PatientAuthController`; `bill.claim_status` and `prescription.rx_status` are stringly-typed throughout. Same defect class, larger blast radius — a follow-up.
- Behaviour change worth knowing: a no-show appointment no longer appears in the FHIR case bundle as an Encounter. Any consumer counting encounters for a patient will see fewer entries than before, which is the point — the previous numbers were wrong.
- `AppointmentStatus.fromCode` returns null instead of throwing, so an unrecognised status from the database degrades to `UNKNOWN` rather than breaking the whole bundle.

## Batch M6 — Crypto & error-handling contract 🟠 ✅ Complete (2026-09-15)

**Goal:** no silent data loss, no disguised programming errors, and a startup guard that actually runs.

### Changes

| # | Change | Files |
|---|--------|-------|
| M6.1 | **`encrypt()` throws instead of returning null.** Encryption cannot fail for one value — the causes are process-wide (no key, broken JCE provider, no memory) — so returning null turned a broken process into silently empty clinical fields: the write succeeded, the caller got 200, and the PHI was gone. Now the write is refused and the transaction rolls back. `encrypt(null)` still returns null, so legitimately empty fields are unchanged, and **the read side deliberately keeps its `[DECRYPT_FAILED]` placeholder**: a single unreadable row must not break a list endpoint, but a value that cannot be encrypted must never be stored as if it were empty | `common/config/AesCryptoUtil.java` |
| M6.2 | The catch-all `IllegalArgumentException` → 400 handler is gone: page bounds are enforced by `Pages` (a `BusinessException`) and `PageQuery` (bean validation), so anything else throwing one is a programming error that should be logged as a 500 rather than dressed up as a 400. In its place, genuine client mistakes are mapped explicitly: malformed JSON / bad path-variable type / missing parameter → **400**, unknown path → **404**, wrong verb → **405** | `common/exception/GlobalExceptionHandler.java` |
| M6.3 | All seven `catch (Exception ignored)` are gone. Audit-write failures during login now warn (they were invisible); the JWT `patientId` claim is converted explicitly instead of cast-and-catch; the audit aspect looks accessors up (`noArgMethod`) instead of catching `NoSuchMethodException` for every argument of every audited call — a probe miss is the normal case, not an error — and the remaining reflection failures log at trace | `AuthService`; `PatientAuthController`; `JwtClaimMapper`; `AuditLogAspect` |
| M6.4 | The restart-consistency check compares a **value** instead of parsing prose: a rotation writes a dedicated `KEY_ROT_FINGERPRINT` audit row whose `detail` is exactly the fingerprint, and the check reads that row. It used to `substring`/`indexOf` the human-readable sentence, so editing the wording would have silently disabled the guard | `AesCryptoUtil.java`; `KeyAudit` (existing columns) |
| M6.5 | The startup audit and the mismatch check moved from `AesCryptoUtil.init()` into `KeyAuditBridge`, which runs once the audit repository exists. **They had never run at all**: a `@PostConstruct` in the crypto bean executes before the bridge sets `keyAuditRepo`, so the null check silently skipped both — `key_audit` contained no `KEY_INIT` row in any boot | `common/config/KeyAuditBridge.java`; `AesCryptoUtil.java` |
| M6.6 | The test-only key seam is now one documented group (`initializeForTest` / `snapshotKeysForTest` / `restoreKeysForTest`), marked as the only entry points that bypass `app.aes.key`/`rotate` | `AesCryptoUtil.java` |

### Verification

- **M6.1 by mutation A/B** (temporarily forcing every `Cipher.getInstance` to fail, then reverting — no test seam was added, since one whose only purpose is to corrupt process state is worse than the untested branch):

  | Code | Result with encryption broken |
  |------|-------------------------------|
  | before (returns null) | **23** `AES-GCM encryption failed — storing null` lines; no exception surfaced — the failures were absorbed (one of them later hit a NOT NULL column, so the seed died with a confusing SQL error) |
  | after (throws) | `IllegalStateException: AES-GCM encryption failed — refusing to write the field…` — the write is refused, nothing is stored |

- **M6.2 live** on a DB copy: `GET /api/v1/patients/abc` → **400** (was 500), malformed JSON → **400** (was 500), `GET /api/v1/no-such-endpoint` → **404** (was 500), `DELETE /api/v1/patients` → **405** (was 500). Normal writes still return 200.
- **M6.4/M6.5 live**: a real `POST /admin/keys/rotate` on a copy wrote `KEY_ROT_FINGERPRINT` with `detail=12ffbe1f59d63d7e` (16 hex, no prose); restarting with the stale config then logged exactly one `KEY ROTATION CONFIG MISMATCH … fingerprint=12ffbe1f59d63d7e but the configured app.aes.key has fingerprint=d629c2ee7381916a`, and `key_audit` gained the `KEY_INIT` row that had never been written before.
- **Two bugs the verification itself exposed**: the event name `KEY_ROTATION_FINGERPRINT` is 24 characters and `key_audit.event_type` is `VARCHAR(20)` — the insert failed at runtime (renamed to `KEY_ROT_FINGERPRINT`, with a comment); and the init-order bug in M6.5 above.
- `mvn clean verify`: **166 tests, 0 failures**, all four enforcer rules passing.

### Notes

- M6.1 is a deliberately visible behaviour change: a PHI write that cannot be encrypted now fails the whole request instead of quietly storing an empty field. This is what makes the failure safe — and `AesAttributeConverter` already refused to persist `[DECRYPT_FAILED]`, so the write side was already fail-closed; only the encrypt path was not.
- Rotation stays safe under the new semantics: a failing batch rolls back, `runBatchRotation` catches it, logs "aborted — will retry on next safety check", and the 3 am `safetyCheck` resumes it (the migration predicate is idempotent).
- A DB with rotations recorded by the *old* prose format has no `KEY_ROT_FINGERPRINT` row, so the mismatch check simply does not fire until the next rotation. Acceptable: the alternative was a schema change plus a forced local-DB rebuild for a guard that was dead anyway.
- The read path still returns `[DECRYPT_FAILED]` as a sentinel string; turning that into a typed failure is a larger design change (it would affect every list endpoint) and is not worth it while the write side is fail-closed.

## Batch M7 — Pagination unification + input bounds 🟠 ✅ Complete (2026-09-14)

**Goal:** one pagination contract with a real bound, and encrypted columns that cannot be overflowed into a 500.

### Changes

| # | Change | Files |
|---|--------|-------|
| M7.1 | New `Pages.of(page, size[, sort])` is the only place a user-facing page becomes a pageable, and the only place the bounds live (`MAX_SIZE = 200`, `DEFAULT_SIZE = 10`). Out-of-range is **rejected with 400, not clamped**. 20 sites across 17 files now route through it — previously every service and several controllers built `PageRequest.of((int)(page-1), (int)size)` by hand, and the raw-`@RequestParam` half had **no bound at all** | `common/base/Pages.java` (new); 17 controllers/services |
| M7.2 | `PageQuery` references `Pages.MAX_SIZE` / `Pages.DEFAULT_SIZE`, so the limit cannot drift between the two binding styles (one numeric source of truth) | `common/base/PageQuery.java` |
| M7.3 | `@Size` on the PHI fields whose columns are `VARCHAR` — the limits are derived, not guessed: hex ciphertext of `1 + 12 + plaintext + 16` bytes means `VARCHAR(n)` fits `n/2 - 29` characters. `medical_history` 4000 → **1971**, `allergies` 2000 → **971**, the `VARCHAR(200)` cluster → **71**, `email` 300 → **121**. One class-level javadoc per DTO explains where the odd numbers come from | `patient/dto/PatientFormDTO.java`; `system/dto/SysUserFormDTO.java`; `system/dto/SysUserUpdateFormDTO.java`; `billing/controller/BillController.java` (claim number) |
| M7.4 | Frontend asked for `size: 999` on the lab page (would now 400) → 200; CLAUDE.md's `size: 999` guidance → 200 with the reason | `views/lab/LabResults.tsx`; `CLAUDE.md` |
| M7.5 | Found while auditing `_count`: FHIR `_count=0` **divided by zero** in the patient search (`offset / maxCount`) and built a zero-size page in the observation search. Both now floor at 1 | `FhirPatientController`; `FhirObservationController` |
| M7.6 | `docs/API-LAYOUT.md`: new Pagination section documenting the cap, the two 400 message shapes and the behaviour change | `docs/API-LAYOUT.md` |

### Verification

- `mvn clean test`: **166 tests, 0 failures**; `npx tsc --noEmit` clean.
- Live against a copy of the seeded DB:

  | Request | Before | After |
  |---------|--------|-------|
  | `GET /api/v1/patients?page=1&size=9999` | 200, tried to serve 9999 | **400 `Size must be between 1 and 200`** |
  | `size=201` / `size=0` | 200 / 500 | **400** both |
  | `size=200` | 200 | 200 (unchanged) |
  | `page=0` | 500 (IllegalArgumentException) | **400 `Page must be at least 1`** |
  | `GET /api/v1/patients/100/vitals?size=9999` (PageQuery) | 200 | **400 `size: Size must be at most 200`** |
  | `POST /api/v1/patients` with a 3 000-char `medicalHistory` | DB error / 500 | **400 `Medical history must be at most 1971 characters`** |
  | same with 1 971 chars | 200 | **200** — the derived boundary is exact |

### Notes

- Deliberately not routed through `Pages`: the CSV export paging loop (server-side scan, `EXPORT_PAGE_SIZE = 500`), the internal existence probes (`of(0, 1)`, `of(0, 20)`) and the FHIR `_count` cap of 500 — none of those are user page requests, and FHIR's own contract sets its cap.
- Two 400 message shapes for one rule is a small wart: raw-parameter endpoints throw `BusinessException` from `Pages`, `PageQuery` endpoints fail bean validation first. Same status, same limit, different wording (documented in API-LAYOUT).
- The patient portal's self-update endpoint still took an untyped body, so its fields could not carry `@Size` — **closed in M8.2**, which replaced it with `PatientSelfUpdateFormDTO` carrying the derived bounds.
- `Pages` bounds are enforced in the service layer for the raw-parameter endpoints rather than at the web layer; if a future round migrates them all to `@Valid PageQuery`, the builder stays the single authority either way.

## Batch M8 — Layering: VO/DTO extraction + controller split 🟠 (in progress)

**Goal:** no raw entity on the wire, no untyped request body, and the module graph becomes acyclic.

> **M8.1 ✅ complete (2026-09-15)** — everything that returned a JPA entity now returns a VO, and staff reads of a patient's record are audited. **M8.2 ✅ complete (2026-09-16)** — the portal's untyped self-update body is a typed, validated DTO. **M8.3 ✅ complete (2026-09-16)** — the portal's business logic lives in services. **M8.4 ✅ complete (2026-09-16)** — the portal controller is six resource controllers, and its guard test caught a systemic 500: every wrong-role request in the API answered `500 Internal server error` instead of 403 (fixed). **M8.6 ✅ complete (2026-09-16)** — the portal export assembles from VOs and owning services, and its live response is byte-identical to before. **M8.5 ⬜ still open.**

### M8.6 — the export stops reaching across modules ✅

**The last two cross-module shortcuts in the portal.** `GET /api/v1/patient/me/export` is the only endpoint left whose controller injects other modules' repositories (`AppointmentRepository`, `PrescriptionRepository`, `PrescriptionItemRepository`, `BillRepository` — plus its own `PatientRepository`), and `PatientDataExport` is the only DTO in `patient/dto` that imports other modules' **entities** (`Appointment`, `Prescription`, `PrescriptionItem`, `Bill`) to build its summaries. M8.3 left both deliberately; this slice closes them.

**Decisions taken before any code (2026-09-16):**

1. **Reuse the existing VOs instead of minting lean export VOs.** `AppointmentService.listForPatient` / `PrescriptionService.listForPatient` / `BillService.listForPatient` return `AppointmentVO` / `PrescriptionVO` / `BillVO`, and `PatientDataExport`'s inner summaries map from those instead of from entities. The alternative — a dedicated `*ExportVO` per module — would duplicate four field maps to avoid loading patient/doctor names that the summaries throw away. Accepted cost: two extra lookups per appointment (the names) and a per-prescription items query where the controller used to batch one (`toVO` does `findByPrescriptionId` per row). Both are bounded by one patient's own record on a rare operation.
2. **The exported JSON must come out identical.** That file is a HIPAA right-of-access artifact the patient downloads (`health-data-<date>.json`), so a renamed or dropped field is a user-visible change to a document, not an internal refactor. The summaries keep their exact field names; the ordering is preserved (appointments DESC `appointmentTime`, prescriptions DESC `prescriptionDate`, bills DESC `createTime`); the `exportDate`/`dataUseNotice` fields stay. A live baseline was captured **before** any code was written.
3. **The audit annotation stays on the controller** (`@Auditable(module = "patient", action = "EXPORT_SELF", phiAccess = true)`) — only the assembly moves, so the audit trail for an export is unchanged.

| # | Change | Files |
|---|--------|-------|
| M8.6.1 | `AppointmentService.listForPatient` / `PrescriptionService.listForPatient` / `BillService.listForPatient` — patient-scoped, unpaged, no doctor scope (the id comes from the token) | `AppointmentService`, `PrescriptionService`, `BillService` |
| M8.6.2 | `PatientDataExport`: `of(PatientVO, List<AppointmentVO>, List<PrescriptionVO>, List<BillVO>)`; the four entity imports are gone; `ItemSummary.from` takes a `PrescriptionItemVO` | `module/patient/dto/PatientDataExport.java` |
| M8.6.3 | `PatientPortalExportController`: five repositories → four services (`PatientService`, `AppointmentService`, `PrescriptionService`, `BillService`); the missing-patient case still answers `Result.ok(null)` | `PatientPortalExportController` |

**Invariants:** no path/verb/parameter/response-shape change; `patient/dto` no longer imports a foreign entity; the *portal* no longer injects a foreign repository.

**What landed:** the portal export controller went from five repositories to four services, `PatientDataExport` from four foreign entity imports to four VO imports, and the assembly to three owning-service reads.

**One planned claim was wrong, and the grep that was supposed to confirm it disproved it.** "No controller in the codebase injects another module's repository" is false: `module/export/controller/ExportController` (CSV streaming, 156 lines, injects `PatientRepository` + `BillRepository` and runs its paged queries inside the `StreamingResponseBody` lambda) and `module/system/controller/EmergencyAccessController` (`PatientRepository`, for a 404 existence check) still do. Foreign-repository injections in controllers: **7 across 3 controllers → 3 across 2 controllers**. Both leftovers are recorded as follow-ups rather than silently claimed fixed:

| Remaining controller → foreign repository | Why it is not a 3-line move |
|---|---|
| `ExportController` → `PatientRepository`, `BillRepository` | The queries execute on the streaming thread, so moving them behind services means settling the transaction boundary there too — its own slice |
| `EmergencyAccessController` → `PatientRepository` | It only needs "does this patient exist"; the honest fix is a `PatientService` existence method, which is a `system → patient` service edge — M8.5's territory |

**Out of scope:** `common → module` reverse dependencies (M8.5), the two controllers above.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **176 tests, 0 failures** (175 → 176), enforcer clean |
| **The exported document, live, before vs. after** | **byte-identical** (both responses parsed and compared with `exportDate` removed): 4 appointments, 2 prescriptions with items, 2 bills, 31 demographics fields, same notice |
| Document shape pinned in the suite | New test asserts the six top-level sections and the exact field set of an appointment summary (7), a prescription summary (6), an item summary (6) and a bill summary (8), plus the absence of `isDeleted`/`version`/`updateTime` |
| `patient/dto` foreign-entity imports (grep) | 4 → **0** |
| Foreign-repository injections in controllers (grep) | 7 across 3 → **3 across 2**, the rest being the two documented leftovers |
| Portal export with a patient token (live) | 200, correct rows (4/2/2) |

**Trade-off accepted and measured:** the summaries now go through the shared VOs, so the export loads patient/doctor names it discards and issues one items query per prescription where the old controller batched a single one (`PrescriptionService.toVO`). For one patient's own record on a right-of-access request this is immaterial; a lean `*ExportVO` per module would have duplicated four field maps to avoid it.

**Files:** `AppointmentService`, `PrescriptionService`, `BillService`, `module/patient/dto/PatientDataExport.java`, `PatientPortalExportController`, `PatientPortalIntegrationTest.java`. No API-LAYOUT change: the surface does not move.

### M8.4 — the portal controller becomes six resource controllers ✅

**Decisions taken before any code (2026-09-16):**

1. **All six controllers stay in `module/patient/controller/`** (option A). A resource-follows-module split — portal appointments in `appointment`, bills in `billing`, prescriptions in `prescription`, the way `RefillController` and `PatientChatController` already sit — was considered and rejected: it would scatter one portal surface across four modules for no dependency win, because the `patient ↔ appointment/billing/prescription` coupling that remains after M8.3 is a *data* dependency (a VO needs the patient's name), not a controller-placement one. Cycle governance belongs to M8.5/M8.6.
2. **The four patient-module repositories the portal reads directly are not folded behind services in this batch** (`VitalSignRepository`, `ProblemRepository`, `ImmunizationRepository`, `CarePlanRepository`). The staff controllers read those same repositories directly, so wrapping only the portal would leave two entry shapes for one table; doing it properly means four new services plus four staff controllers, which is its own batch (candidate for M11).

**Why six and not the five groups in the Round 50 row:** 18 endpoints do not fit five homes — `/disclosures`, `/referrals`, `/prior-auths` and `/export` had no assigned owner. `/export` alone needs five repositories (it assembles `PatientDataExport`, M8.6), so it cannot be folded into a read controller without dragging them along. Six classes with disjoint paths cover all 18 endpoints and leave no residual god-class.

| Controller (all `@RequestMapping("/api/v1/patient/me")`) | Endpoints | Dependencies |
|---|---|---|
| `PatientPortalProfileController` | GET `/`, PUT `/`, PUT `/password`, GET `/disclosures` | `PatientService`, `PatientAccountService`, `AuditLogRepository` (3) |
| `PatientPortalClinicalController` | GET `/observations`, `/observations/trend`, `/vitals`, `/problems`, `/immunizations`, `/care-plans` | `LabAnalysisService` + 4 own-module repositories (5) |
| `PatientPortalAppointmentController` | GET `/appointments`, PUT `/appointments/{id}/cancel`, GET `/referrals`, GET `/prior-auths` | `AppointmentService`, `ReferralService`, `PriorAuthService` (3) |
| `PatientPortalPrescriptionController` | GET `/prescriptions` | `PrescriptionService` (1) |
| `PatientPortalBillingController` | GET `/bills`, PUT `/bills/{id}/pay` | `BillService` (1) |
| `PatientPortalExportController` | GET `/export` | 5 repositories — deliberately visible, this is M8.6's target (5) |

**Invariants that must not move** (the split is a file move, not a behaviour change):

- Every class carries class-level `@PreAuthorize("hasRole('PATIENT')")` — six places to get right where there was one. A new test calls one endpoint of **each** controller with an ADMIN token and expects 403, so a forgotten annotation fails the suite instead of opening an endpoint.
- Paths, verbs, `@RequestParam`s, response types and status codes unchanged; every `@Auditable(module, action, phiAccess)` stays on the same operation with the same values, so the audit trail is byte-identical.
- Mechanical proof of no surface change: the 18 `@*Mapping` annotations are captured before and after the move and diffed (verb + path pairs must be identical), and the 13 portal integration tests must pass untouched.

**Out of scope for M8.4:** `PatientDataExport`'s cross-module assembly (M8.6), the four repository-backed reads (see decision 2), `common → module` (M8.5).

**What landed:** `PatientPortalController` (241 lines, 18 endpoints, 18 dependencies) is gone; the six classes above replace it, the largest being 5 dependencies. No path, verb, parameter, return type or `@Auditable` annotation changed.

### M8.4.7 — the guard test exposed a systemic 500: every wrong-role request ✅

The role-boundary test below was written to catch a forgotten `@PreAuthorize` after the split. Its first run failed — not with 200, but with **500**: the first probe (`ADMIN` token on `GET /api/v1/patient/me`) was answered by the global handler's catch-all.

Cause: `SecurityConfig` guards the URL tree with `authenticated()` only (`anyRequest().authenticated()`), so **every** role check in this application is a `@PreAuthorize` on a controller method. Its `AuthorizationDeniedException` is thrown by the method interceptor *inside* the DispatcherServlet — before `ExceptionTranslationFilter` could turn it into 403 — so the `@ExceptionHandler(Exception.class)` catch-all claimed it and reported "no permission" as `500 Internal server error`, with an ERROR stack trace. Nothing had ever exercised that path: every existing 403 assertion in the suite comes from `DoctorPatientScope.requireAccess`, which throws a `BusinessException` (a different path entirely).

Measured on the running M8.3 build before the fix:

| Request | Before | After |
|---|---|---|
| Patient token → `GET /api/v1/patients` | 500 | **403** `Access denied` |
| Doctor token → `GET /api/v1/audit-logs` (ADMIN-only) | 500 | **403** `Access denied` |
| Staff token → `GET /api/v1/patient/me` | 500 | **403** `Access denied` |

The fix is one handler in `GlobalExceptionHandler` for `org.springframework.security.access.AccessDeniedException` (the parent of `AuthorizationDeniedException`), returning the same `{code:403,message:"Access denied"}` body as `BusinessException(FORBIDDEN)`, so a client cannot tell a role denial from a scope denial. API-LAYOUT's error-contract table already promised 403 here — that row was aspirational until now, and the doc says so.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **175 tests, 0 failures** (173 → 175), enforcer clean |
| Endpoint inventory, before vs. after the move | 18 → 18, **set-identical** (verb + full path, diffed) |
| `@Auditable` annotations, before vs. after | 9 → 9, **set-identical** (module/action/phiAccess) |
| Class-level `@RequestMapping` + `@PreAuthorize` on all six | 6/6 present (checked mechanically; this is the risk the split introduced) |
| Portal integration tests | 13 pre-existing tests unchanged and green; the 14th is the new role guard |
| Wrong-role probes (live, after restart on the M8.4 build) | 403 `Access denied` on all three — was 500 |
| Every portal read with a patient token (live) | profile, observations (30), vitals, problems, immunizations, care plans, appointments, prescriptions, referrals, bills, prior-auths, disclosures (19) — all **200**, correct rows |
| Audit trail after the split (live) | `myVitals`/`myProblems`/`myObservations`/`myReferrals`/`myPriorAuths`/`EXPORT_SELF` rows still written with `patientId=100` and the same module/action pairs |

**Files:** `module/patient/controller/PatientPortal{Profile,Clinical,Appointment,Prescription,Billing,Export}Controller.java` (6 new), `PatientPortalController.java` (deleted), `common/exception/GlobalExceptionHandler.java`, `PatientPortalIntegrationTest.java`, `GlobalExceptionHandlerTest.java`, `docs/API-LAYOUT.md`. No documented API surface moved.

### M8.3 — the portal's business logic moves into services ✅

`PatientPortalController` was not a controller: 18 injected beans, three of them other modules' `*Repository`, plus the cancellation rules, the payment ownership check, the password-change flow and the VO assembly (doctor/patient name lookups) written out inline. Nothing below the web layer could reuse any of it, and `patient` reached into `system`, `appointment`, `billing` and `prescription` at the repository level.

| # | Change | Files |
|---|--------|-------|
| M8.3.1 | **Password-history policy existed three times** — `PatientPortalController`, `UserProfileController` and `SysUserService` each built a `PasswordHistory` row and each re-implemented the `findTop3…` + `matches` check. Now `PasswordHistoryService` (`requireNotReused` / `record`) is the only copy; the three call sites keep their own `userType` and identity (staff credentials key on `SysUser.id`, patients on `PatientAuth.id` — the same table, two id spaces) | `module/system/service/PasswordHistoryService.java` (new); `UserProfileController`; `SysUserService`; `PatientAccountService` |
| M8.3.2 | Portal password change moved to `PatientAccountService.changePassword(patientId, old, new)`, taking the credential id from `PatientAuth`, not from the request | `module/patient/service/PatientAccountService.java` (new) |
| M8.3.3 | Appointment cancellation moved to `AppointmentService.cancelByPatient(id, patientId)` — ownership check, terminal-state check and the past-appointment check all in one place instead of in a controller | `AppointmentService` |
| M8.3.4 | Bill payment moved to `BillService.payByPatient(id, patientId, amount, method)`; the payment maths is now one private `applyPayment` shared by the staff path (`pay`) and the portal path, and `findBill` removes the duplicated lookup. Ownership is checked *before* the state check, so a foreign bill is 403 regardless of its status | `BillService` |
| M8.3.5 | Patient-scoped listings moved to their owning services: `AppointmentService.pageForPatient` (appointments by `appointmentTime`), `PrescriptionService.pageForPatient` (`createTime`), `BillService.pageForPatient` (`createTime`), all without doctor scope — the id comes from the token. This is what removes `PatientRepository`/`SysUserRepository`/`PrescriptionItemRepository` from the portal controller: the VO assembly (doctor/patient names, prescription items) now happens inside the owning module | `AppointmentService`, `PrescriptionService`, `BillService` |
| M8.3.6 | `ReferralService.listByPatient` and `PriorAuthService.listByPatient` are new — `referral` lives in `appointment` and `prior_auth` in `billing`, so the portal was querying two foreign repositories to render two read-only pages. `ReferralController.listByPatient` now delegates to the same method (its `doctorPatientScope.requireAccess` stays where it was) | `module/appointment/service/ReferralService.java`, `module/billing/service/PriorAuthService.java` (new); `ReferralController` |
| M8.3.7 | Profile read/update moved to `PatientService.profile` / `updateOwnProfile`. `profile` is deliberately **not** audited and still returns `null` for a missing row: a patient reading their own record is the baseline case and a row per page load would bury the real accesses in `/patient/me/disclosures` | `PatientService` |
| M8.3.8 | The two controller-nested request classes became DTOs in the module's `dto/`: `PatientPasswordChangeFormDTO`, `PatientPayBillFormDTO`. The latter adds `@NotNull` on `paymentAmount` — previously a null amount passed `@Positive` (which ignores null) and then hit `paid.add(null)` → NPE → **500** | `module/patient/dto/` |
| M8.3.9 | **M8.1 leftovers found while tracing the emergency controller:** `GET /api/v1/emergency/history` still returned `List<EmergencyAccess>` (a JPA entity, so `isDeleted`/`version`/`updateTime` on the wire) and `POST /access/{patientId}` returned an untyped `Map<String, Object>`. Now `EmergencyAccessVO` and `EmergencyAccessTokenVO` | `module/system/dto/` (new); `EmergencyAccessController` |
| M8.3.10 | `EmergencyAccessResultVO.expiresIn` in the frontend was never sent — the backend has always returned `expiresInMinutes`. Dead field, but the type lied; renamed in TS | `medical-web/src/types/entities.ts` |
| M8.3.11 | The patient's bill page offered **Pay Now** for `DRAFT` bills, but `applyPayment` only accepts `PENDING`, so that button could only ever produce a 409. Hidden for DRAFT | `views/patient/bills/index.tsx` |

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **173 tests, 0 failures** (166 → 173; the portal class went from 6 to 13), enforcer clean |
| Cancel own future appointment | **200**, status → 2 in the patient's own list; a second attempt → **409** `Appointment already cancelled or completed` |
| Cancel another patient's appointment | **403** — the ownership check lives in the service now, not in the controller |
| Cancel a past appointment (seed 202) | **409** `Cannot cancel past appointments` |
| Pay seeded bill 501 (37.80, PENDING) | **200** → `claimStatus` `PAID` in the patient's list |
| Pay another patient's bill (502) | **403** (ownership first, so the PAID state of 502 is irrelevant) |
| Wrong old password | **400** `Old password is incorrect` |
| Change → change → reuse the first new password | 200, 200, then **400** `New password must not match any of the last 3 passwords` — proves the replaced hash was recorded, which is exactly what the three-way duplication made easy to get wrong |
| `npm run check` | tsc clean; eslint 0 errors, same 4 warnings as before this batch |

**Test isolation fixed on the way:** the new portal tests mutate shared seed state, so `cleanup-test-data.sql` now restores bill 501 to `PENDING`/unpaid and `patient1`'s password hash (plus deletes `PATIENT` password history). Without that, the billing "2 PAID bills" assertion and every later `patientLogin("patient1", …)` would fail depending on class order.

**Behaviour changes to know about**

- `PUT /api/v1/patient/me/bills/{id}/pay` with a missing `paymentAmount` is now **400**, not 500.
- `GET /api/v1/emergency/history` no longer exposes `isDeleted`/`version`/`updateTime`/`createTime` (VO instead of entity); `POST /api/v1/emergency/access/{patientId}` is typed as `{token, expiresInMinutes, patientId}`.
- The audited portal operations now carry a `targetId` on their audit row (`appointment`/`billing`/`patient` id); previously the row for e.g. a patient password change had `targetId = null`. Action names, modules and the `patientId` are unchanged.
- Still open from M8.1's scope, deliberately not touched here: the remaining `Map<String, Object>` responses in the integration/ePrescribing/quality report endpoints.

### M8.2 — the portal's untyped request body ✅

`PUT /api/v1/patient/me` took a `Map<String, Object>` and applied twelve fields by hand with `if (body.containsKey(...))` and `(String)` casts — no validation, no types, and a silently ignored field for every staff-verified value a client sent along.

| # | Change | Files |
|---|--------|-------|
| M8.2.1 | New `PatientSelfUpdateFormDTO`: exactly the twelve fields a patient may change, each with the `@Size` bound derived from its encrypted column (200 → 71 plaintext characters, 300 → 121, and the unencrypted `emergencyContactRelation` → its own 50). Conversion lives in the DTO (`applyTo`) | `module/patient/dto/PatientSelfUpdateFormDTO.java` (new); `PatientPortalController` |
| M8.2.2 | The frontend now sends only the editable fields (`FIELDS.filter(f => !f.readonly)`) instead of the whole profile object with `?? null`, so the contract is what the request actually contains — the old payload carried `name`, `mrn`, `insurancePayer` and `allergies`, which the backend ignored | `views/patient/profile/index.tsx` |

**Verification** (live, on a DB copy):

| Check | Result |
|-------|--------|
| All twelve fields updated | **200**, and a read-back shows the new phone/email/city/ZIP/emergency contact |
| Sending `name`, `allergies`, `insurancePayer`, `mrn` alongside | **200** but **none of them changed** — they are not part of the DTO, so they cannot be set from here |
| `phoneMobile` 71 chars / 72 chars | **200** / **400** `phoneMobile: Phone (mobile) must be at most 71 characters` |
| `email` 121 / 130 chars | **200** / **400** `email: Email must be at most 121 characters` |
| `emergencyContactRelation` 60 chars | **400** (unencrypted `VARCHAR(50)`) |

- `mvn clean verify`: 166 tests, 0 failures; `npx tsc --noEmit` clean; `npm run lint` unchanged (0 errors, 4 pre-existing warnings).

**Behaviour change to know about:** the endpoint is now a true full update — a field omitted from the body is cleared, where previously only the keys present were touched. The portal submits all twelve, so its behaviour is unchanged; a client sending a partial body will now clear the rest. PUT semantics, and documented in API-LAYOUT.

### M8.1 — no raw entities on the wire ✅

The audit found **12 endpoints still returning entities**, not the 5 the plan listed (the original grep missed `LabResultController`, `PatientController.getHistory/getAllergies` and `KeyAuditController`). Seven of them could reuse a VO the staff side already had; five needed a new one.

| # | Change | Files |
|---|--------|-------|
| M8.1.1 | Portal endpoints now map to the VOs the staff endpoints already return: `myVitals` → `VitalSignVO`, `myProblems` → `ProblemVO`, `myImmunizations` → `ImmunizationVO`, `myReferrals` → `ReferralVO`, `myCarePlans` → `CarePlanVO`, `myPriorAuths` → `PriorAuthVO`, `myDisclosures` → `AuditLogVO` (were raw `VitalSign`/`Problem`/…/`AuditLog`) | `PatientPortalController` |
| M8.1.2 | Five new VOs for the rest: `ConsentVO`, `QualityMeasureVO`, `QualityResultVO`, `PharmacyVO`, `FormularyEntryVO`, plus `MedicalHistoryEntryVO`, `AllergyEntryVO`, `LoincCatalogVO` and `KeyAuditVO` for the endpoints the plan had missed | `module/*/dto/`, `common/audit/KeyAuditVO.java` |
| M8.1.3 | `QualityMeasureVO` deliberately drops `denominatorQuery`, `numeratorQuery` and `exclusionQuery`: the measure endpoints were publishing the raw SQL behind each eCQM measure | `module/quality/dto/QualityMeasureVO.java` |
| M8.1.4 | `PharmacyVO` normalises the 0/1 column into a real boolean — the TypeScript type already said `supportsEpcs?: boolean` while the API sent `1`/`0` | `module/prescription/dto/PharmacyVO.java` |
| M8.1.5 | **Found while verifying**: `patient_id` was null on every portal audit row, so the patient's own "who accessed my record" view (`GET /patient/me/disclosures`) returned **0 rows, always**. Neither resolution path could fire: `myVitals(loginUser)` has no `patientId` argument, its module is not `patient`, and the result is a list. The aspect now falls back to the principal — a PATIENT token's user id *is* the patient id, and a break-glass token carries the patient it was issued for | `common/audit/AuditLogAspect.java` |

**Verification (contract diff against the previous build, captured live before the change):**

| Endpoint group | Result |
|----------------|--------|
| staff vitals / problems / immunizations / care-plans / referrals / pharmacy / profile | **byte-identical field sets** — they already used VOs |
| portal vitals / problems / immunizations / referrals / care-plans / prior-auths | only `isDeleted`, `updateTime`, `version` removed — JPA bookkeeping the frontend never reads (checked: those names appear nowhere in `views/`, `api/` or the TS types) |
| quality measures | the three SQL fields removed, every other field identical by value |
| pharmacy | `supportsEpcs`: `1`/`0` (int) → `true`/`false` (boolean) — matches the TS type for the first time |
| portal disclosures | 0 rows before → **1 row with `patientId=100`** after the aspect fix |

- `mvn clean verify`: 166 tests, 0 failures. `npx tsc --noEmit` clean. No controller returns an entity any more (re-checked by grep).

### M8.1.6 — staff reads of a patient's record now leave a trace ✅

Opening a patient's vitals wrote no audit row: `VitalSignController`'s only `@Auditable` was on `create`, so a doctor could read a full clinical history and leave nothing behind — and the portal's access-history view (the feature built to show exactly that) could not show it.

While fixing it, my first reading of the codebase was wrong and worth recording: read auditing was **not** absent. `PatientService.getById` (`VIEW`), `PatientController.getHistory`/`getAllergies` (`VIEW_HISTORY`/`VIEW_ALLERGIES`), `FhirPatientController` and `FhirObservationController` (`FHIR_VIEW`) were already audited — my initial survey looked only at controller annotations a few lines above each `@GetMapping` and missed them. The real gap was the **clinical list endpoints**, which have no service layer (they use repositories directly), so there was nowhere for a service-level annotation to live.

| # | Change | Files |
|---|--------|-------|
| M8.1.6 | `@Auditable(..., action = "VIEW", phiAccess = true)` added to the single-patient clinical reads: vitals, problems, immunizations, care plans, consent, referrals (list + by-patient), lab observations + trend, prescription detail + by-patient, and the FHIR case bundle (`FHIR_VIEW`). Deliberately not added to list/search endpoints with no single patient (patient search, FHIR search) or to reference data (LOINC catalog): those rows could not be attributed to a patient, so they would add volume without making the access history better | `VitalSignController`, `ProblemController`, `ImmunizationController`, `CarePlanController`, `ConsentController`, `ReferralController`, `LabResultController`, `PrescriptionController`, `PatientCaseController` |

**Verification** (live, on a DB copy): a doctor read five things about patient 100 — vitals, problems, observations, allergies, and the FHIR case bundle. The patient's own access history then returned **5 rows**:

```
patient      FHIR_VIEW       patientId=100
patient      VIEW_ALLERGIES  patientId=100
observation  VIEW            patientId=100
problem      VIEW            patientId=100
vital_sign   VIEW            patientId=100
```

Before the change the same five reads produced **0** rows — and 0 rows were visible to the patient. `mvn clean verify`: 166 tests, 0 failures.

**Also noted:** the portal's disclosures response carries `rowHash`/`prevHash` (the tamper-evidence hashes) because it reuses `AuditLogVO`; the page uses none of them. A narrower portal VO would be tidier if that list ever grows.

### Changes

| # | Change | Files |
|---|--------|-------|
| M8.1 | Add VOs (+ `fromEntity`) for the endpoints currently returning entities: portal `vitals/problems/immunizations/referrals/care-plans/prior-auths/disclosures`, `ConsentController`, `QualityController`, `PharmacyController`, `FormularyController` | new DTOs under each module's `dto/`; `PatientPortalController:314-359`; `ConsentController:31,64`; `QualityController:23,39`; `PharmacyController:21`; `FormularyController:40` |
| M8.2 | `PUT /api/v1/patient/me`: `Map<String,Object>` → `PatientSelfUpdateFormDTO` (the 12 allowed fields + `@Size`); frontend sends only the editable fields | `module/patient/dto/PatientSelfUpdateFormDTO.java` (new); `PatientPortalController:103-127`; `medical-web/src/views/patient/profile/index.tsx:44,73` |
| M8.3 ✅ | Move portal business logic out of the controller: password change (+ history), appointment cancel rules, bill payment → services. **Delivered differently than planned:** there was no `AuthService` password-history pattern to reuse — the policy was copied in *three* places, so M8.3.1 extracted `PasswordHistoryService` instead. The module **cycles are not gone**, and the dependency *count* did not drop either (18 fields before, 18 after — the foreign repositories that left were replaced by services): `patient` still imports `system`/`appointment`/`billing`/`prescription`. What changed is the **kind** of dependency — the portal's operations go through owner services. The one exception is `GET /patient/me/export`, which still queries four foreign repositories (`AppointmentRepository`, `PrescriptionRepository`, `PrescriptionItemRepository`, `BillRepository`) to assemble `PatientDataExport`; that is M8.6's target and the code says so | `module/system/service/PasswordHistoryService.java` (new); `PatientAccountService` (new); `AppointmentService`; `BillService`; `PrescriptionService`; `PatientService`; `ReferralService`/`PriorAuthService` (new); `PatientPortalController` |
| M8.4 ✅ | Split `PatientPortalController` (18 deps, 18 endpoints) by resource — **delivered as six controllers**, not the five groups listed here, which left `/disclosures`, `/referrals`, `/prior-auths` and `/export` without a home. Also fixed the systemic wrong-role → 500 found by its guard test | `module/patient/controller/PatientPortal*Controller.java`; `common/exception/GlobalExceptionHandler.java` |
| M8.5 | Remove `common → module` reverse deps: relocate `common/job/AppointmentScheduler`, `QualityScheduler` into their modules (and `DataRetentionJob` into a dedicated retention component that owns its cross-module queries); reassess `DoctorPatientScope`'s location | `common/job/*`; affected module packages |
| M8.6 ✅ | `patient/dto/PatientDataExport.java` assembling appointment/prescription/billing entities → reduced to **VOs + three owning-service reads**; a `common`-level DTO was rejected (it would put module DTOs in `common`, the opposite of M8.5). Two other controllers still inject foreign repositories and are recorded as follow-ups | `PatientDataExport.java`; `PatientPortalExportController`; `AppointmentService`/`PrescriptionService`/`BillService` |

### Verification

- Every changed endpoint returns the same JSON field names as before (compare one response per endpoint before/after) — this batch must be contract-preserving except for `PUT /patient/me` extras.
- A dependency check shows no `common → module` imports and no `patient↔{system,appointment,prescription,billing}` cycle.
- Patient portal flows still work: profile read/update, password change, cancel appointment, pay bill, export.
- `mvn test` green; `npx tsc --noEmit` clean.

### Notes

- Largest batch — land M8.1 → M8.2 → M8.3/8.4 → M8.5/8.6 as separate commits, each independently green.

## Batch M9 — Tooling guardrails, dead code, doc sync ⚪ ✅ Complete (2026-09-15)

**Goal:** the CLAUDE.md review checklist becomes executable, and the docs stop contradicting the code.

### Changes

| # | Change | Files |
|---|--------|-------|
| M9.1 | **ESLint** (flat config) with `typescript-eslint` + `react-hooks`, plus `npm run lint` / `typecheck` / `check`. The rules encode the checklist CLAUDE.md re-checked by hand every round: no empty `catch {}` (a failure the user never sees), no unused vars/imports, `eqeqeq`, `no-console` except warn/error, `any` as a warning. Deliberately not type-aware — that needs a type-clean tree and triples lint time, and `typecheck` already covers the type side | `medical-web/eslint.config.mjs` (new); `package.json` |
| M9.2 | `@types/react` / `@types/react-dom` aligned to **18** (they were 19 against React 18 — types for the wrong major). `tsc` stays clean afterwards | `package.json` |
| M9.3 | `maven-enforcer-plugin` on `verify`: Maven ≥3.8, Java ≥17, no duplicate POM dependency versions, **dependencyConvergence** | `medical-server/pom.xml` |
| M9.4 | The convergence rule immediately found a real conflict the build had been hiding: `hapi-fhir-base` reachable at **7.4.0** directly and **6.4.1** through `org.hl7.fhir.utilities`/`r4` (6.3.11). Maven's nearest-wins already picked 7.4.0, so pinning it in `dependencyManagement` changes nothing at runtime and documents the resolution — the tree is now convergent | `medical-server/pom.xml` |
| M9.5 | Dead code: deleted `util/CsvUtil` (0 callers, and its escaper lacked the formula-injection guard the real one has), removed the empty `com/martin/medical` tree, dropped the unused `hutool` dependency (3 `StrUtil.isBlank` → Spring's `StringUtils.hasText`), and made `springdoc` use the version property that was already declared instead of a duplicate literal | `util/CsvUtil.java` (deleted); `SysRoleService`; `SysUserService`; `PatientService`; `pom.xml` |
| M9.6 | Logout key lists were duplicated by hand — `StaffLayout` removed eight keys plus two emergency keys one call at a time, and `PatientLayout` carried the *same* three-key list **twice** (idle timeout + sidebar). `tokenStore` now owns scoped clearers (`clearAll` / `clearPatient`), so the key names live in one place; the emergency-credential rule is enforced there instead of in a comment | `utils/auth.ts`; `layout/StaffLayout.tsx`; `views/patient/layout/PatientLayout.tsx` |
| M9.7 | Doc sync: `docs/API-LAYOUT.md` said `PUT /bills/{id}/pay` allows DOCTOR (the code is ADMIN-only) and still listed the deleted `CsvUtil`; CLAUDE.md's springdoc row said 2.6.0 (the pom has 2.7.0) and its Util row listed the removed Hutool. CLAUDE.md §8 now requires `mvn clean test` and documents the per-class token pattern from M4 | `docs/API-LAYOUT.md`; `CLAUDE.md` |
| M9.8 | README gains a "Checks before committing" section (`mvn clean verify`, `npm run check`, `npm run build`) with the reason `clean` matters | `README.md` |
| M9.9 | Found by running the new `check` script on a machine with **Node 16** active: `eslint` dies with `ConfigError: … structuredClone is not defined`, which looks like a config bug and is really a Node-version problem (`structuredClone` is Node 17+; ESLint 9 wants `^18.18 \|\| ^20.9 \|\| >=21.1`). `.nvmrc` was pointing at **23** — an odd-numbered, non-LTS release that is not even installed on this machine, so `nvm use` failed and the shell fell back to whatever was active. `.nvmrc` → **22** (installed LTS), `engines.node: ">=20.9"` declared, and the README states the floor plus the exact misleading error | `medical-web/.nvmrc`; `medical-web/package.json`; `README.md` |

### Verification

- `npm run check` → **0 errors, 4 warnings** (two `exhaustive-deps`, two `any` in `AuditLogs.tsx` — left as warnings on purpose). The 9 errors it started with were all real: 6 unused imports, an unused map index, an unused state value, and two `catch {}` that swallowed a failed logout call.
- `mvn clean verify` → **166 tests, 0 failures**; all four enforcer rules pass (before the HAPI pin, `dependencyConvergence` failed with the 6.4.1/7.4.0 split shown above).
- `npm run build` clean (433 kB, unchanged shape).
- `eslint.config.mjs` (not `.js`) so Node stops warning about the module type on every run.
- Reproduced the Node-16 failure on purpose (`PATH=…/v16.20.2/bin npx eslint .`) to confirm the diagnosis instead of guessing: identical `ConfigError … structuredClone is not defined`. With Node 22 the same command is clean.

### Notes

- The tooling now fails for the *right* reason on an old Node: `engines` warns at install time, `.nvmrc` points at an installed LTS, and the README names the symptom (`structuredClone is not defined`) so it is not mistaken for a broken config.
- **SpotBugs is deliberately not added**, despite being listed as optional in this plan: on a codebase this size its first run is hundreds of findings that need triage to a baseline, and a guard that always fails is worse than no guard. It deserves its own round with an explicit baseline; the enforcer covers the build-level class of problem.
- The lint warnings are the honest residue: `chat/index.tsx` and `ConfirmDialog.tsx` have `exhaustive-deps` gaps (stale-closure risk — the exact class CLAUDE.md flags) and `AuditLogs.tsx` uses `any`. Fixing the two hook dependencies means touching effect wiring, which belongs with M8's portal/controller work rather than a cleanup batch.
- `dependencyConvergence` will now fail the build if a future dependency adds a second version of anything — the guard is live, not decorative.
- CI is still **not** added (local demo); `npm run check` + `mvn clean verify` are the two commands a CI job would run.

## Batch M10 — Rate limiter config actually applies 🟠 ✅ Complete (2026-09-14)

**Goal:** the configured rate limits are the limits that apply, and the 429 message stops lying about them.

> Reproduced before the fix: an instance started with `--app.rate-limit.export-per-hour=3` served **21 consecutive exports as HTTP 200**. The Redis state explained it — `rate:export:<ip>` held `rate=3` (so the property *was* read), but the companion keys `{rate:export:<ip>}:value` (remaining permits, 82 left over from an earlier 100/hour configuration) and `{rate:export:<ip>}:permits` (an 18-entry sliding window) had survived, and Redisson's `trySetRate` only initialises a limiter that does not yet exist.

### Changes

| # | Change | Files |
|---|--------|-------|
| M10.1 | The effective limit is now part of the limiter key — `rate:export:100:3600:<ip>` — so a changed limit starts from a fresh limiter instead of inheriting a stale budget. `trySetRate` stays per-request (no new JVM state) and is now always called with a rate/interval that matches the key | `common/config/RateLimiterConfig.java` |
| M10.2 | The 429 body is built from the configured value (`"…Max %d exports per hour."`) instead of the hardcoded "Max 5", which was wrong whenever `export-per-hour` differed | same |
| M10.3 | The four ~25-line inline filters collapse into one `register(redissonClient, Limiter)` factory over a `Limiter(keyPrefix, rate, interval, messageTemplate, urlPatterns)` record — 130 → 99 lines, endpoints now declarative. Dropped the redundant `getRequestURI().contains("/login")` guard (the urlPatterns already scope it) and the inline FQN annotations | same |
| M10.4 | README: the limiter key format, that a changed limit now applies immediately, and the **correct** cleanup one-liner — `*rate:*`, not `rate:*` | `README.md` |

### Verification

- **A changed limit applies without touching Redis** — the exact case that failed before: with `export-per-hour=3` exhausted, restarting against the same Redis with `5` and no key cleanup gave `200 200 200 200 200 429 429`, with `rate:export:3:3600:<ip>` (inert) sitting beside the live `rate:export:5:3600:<ip>`.
- 429 body taken from config: `{"code":429,"message":"Export rate limit exceeded. Max 3 exports per hour."}` — and `Max 5` after the change.
- All four limiters still enforce: login `10× 200` then `429`; keys show `rate:login:10:60`, `rate:refresh:20:60`, `rate:export:<n>:3600`, `rate:password-reset:5:60`.
- `mvn test`: **166 tests, 0 failures**; both instances booted with limiters on and no key cleanup between runs.
- **Found while verifying:** Redisson keeps **three** keys per limiter — the config hash plus `{…}:permits` and `{…}:value`. Deleting only the first (my first attempt at resetting the login counter) left the consumed budget behind, so the limiter still fired at #7 instead of #11. The README command was corrected to `*rate:*` accordingly.

### Notes

- Keys left over from a superseded limit are inert, not a correctness problem. They have no TTL, so a long-lived deployment that re-tunes limits often would accumulate them; the README one-liner clears everything.
- Only `export-per-hour` is configurable today; the other three rates are literals in the factory. Making them `@Value`s is now a one-line change each if it is ever wanted.
- Limits stay per-IP (`getRemoteAddr()`), so behind a proxy they key on the proxy address — pre-existing, out of scope here.

## Round completion criteria

1. All 9 batches ✅ with their own `mvn test` / `tsc` / `npm run build` evidence recorded above.
2. `docs/API-LAYOUT.md`, `CLAUDE.md`, `README.md` refreshed for every behaviour change in this round (M1 Redis optionality + H2 reset, M2 export path, M7 size bound, M8 payloads).
3. No new runtime dependency; one removed (`hutool`).
4. Re-run of the review checklist: the F1–F14 items above closed or explicitly deferred with a reason.
