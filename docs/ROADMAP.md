# Project Evolution Roadmap

> From the HIPAA + FHIR + US-Model foundation, through CDS, ePrescribing, compliance audit, frontend migration, multi-agent workflow, clinical data immutability, and full patient portal.
>
> **Backend-focused review (2026-10-15), baseline `4f629f4`: 11 findings (5 P1, 6 P2) — **all 11 fixed**. Each one records the red state it was observed in before the change and the regression test that now holds it; see [Round 53](#round-53--backend-focused-review-follow-up-2026-10-15--complete).**
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
> **Maintainability review (2026-09-11): independent code-quality review (backend 212 main + 7 test Java files; frontend 81 TS/TSX + 6 CSS; schema, pom and all config) → 15 findings (4 🟡 HIGH, 10 🟠 MEDIUM, 1 ⚪ LOW), tracked as Round 50: Maintainability Pass — 10 of 10 batches done (M1–M10; M8's six slices all landed 2026-09-16) — F15 closed by M10, F3 by M4, F4 by M5, F6+F10 by M6, F8+F11 by M7, F12 by M9. Scope decision: H2-only learning demo ⇒ DB migration tooling out of scope (finding withdrawn; only H2-file hygiene kept as F14/M1). Headline finding, verified at boot: the documented h2 quick start could not boot without Redis (README claimed "no external dependencies") — fixed in M1. See the Round 50 section at the end.**

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
    abnormal_flag CHAR(1),                -- N/L/H/LL/HH  (the column only ever held one character: Round 51.4 folds HH/LL down at the intake)
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
| 7.2 | **Automated Abnormal Flagging** | Auto-set `abnormal_flag` (N/L/H/LL/HH/AA) based on reference range, supporting age/gender-stratified reference ranges. **Never wired, and deleted in Round 51.6**: `LabAnalysisService.autoFlag()` had no caller and its `LL`/`HH` levels could not fit the column |
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

# Round 50: Maintainability Pass ✅ Complete (2026-09-11)

> Independent code-quality review (backend 212 main + 7 test Java files / 13,916 LOC main + 2,782 LOC test; frontend 81 TS/TSX / 6,360 LOC + 6 CSS / 175 LOC; schema, pom and all config). **Ranked for code quality and long-term maintainability, not compliance.**
>
> **Scope decision (user, 2026-09-11): H2-only learning demo.** DB migration tooling is **out of scope** — no Flyway/Liquibase, no MySQL schema-evolution work, no prod deployment hardening. The review's "no migration mechanism" finding is withdrawn on that basis; only the H2-file-staleness footgun it implies survives (F14/M1).
>
> Status: **M1–M10 ✅ all complete (2026-09-11 → 2026-09-16).** M8 (M8.1–M8.6) finished on 2026-09-16. M10 was pulled ahead of M4–M9 because it is a functional defect, not cleanup. Each batch below flips to ✅ individually when it lands.

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

## Batch M8 — Layering: VO/DTO extraction + controller split ✅

**Goal:** no raw entity on the wire, no untyped request body, and the module graph becomes acyclic.

> **M8.1 ✅ complete (2026-09-15)** — everything that returned a JPA entity now returns a VO, and staff reads of a patient's record are audited. **M8.2 ✅ complete (2026-09-16)** — the portal's untyped self-update body is a typed, validated DTO. **M8.3 ✅ complete (2026-09-16)** — the portal's business logic lives in services. **M8.4 ✅ complete (2026-09-16)** — the portal controller is six resource controllers, and its guard test caught a systemic 500: every wrong-role request in the API answered `500 Internal server error` instead of 403 (fixed). **M8.6 ✅ complete (2026-09-16)** — the portal export assembles from VOs and owning services, and its live response is byte-identical to before. **M8.5 ✅ complete (2026-09-16)** — nothing outside a module imports a module any more, and a guard test keeps it that way. **Batch M8 is complete.**

### M8.5 — nothing outside a module imports a module ✅

**Measured first, not remembered.** The inventory below is the output of `grep -rn "import com.example.medical.module." common/ security/`, which is also the check that has to come back empty when this slice lands:

| File | What it imports from a module | Disposition |
|---|---|---|
| `common/job/AppointmentScheduler` (52 lines) | `appointment.entity.Appointment`, `AppointmentStatus`, `appointment.repository.AppointmentRepository` | **relocate** to `module/appointment/service/` |
| `common/job/QualityScheduler` (21 lines) | `quality.service.QualityMeasureService` | **relocate** to `module/quality/service/` |
| `common/job/DataRetentionJob` (47 lines) | five module repositories | **five of the six are dead injections** — only `AuditLogRepository` is ever used, and `softDeleteRetentionDays` is never read. Delete the dead ones |
| `common/security/DoctorPatientScope` | `appointment` + `prescription` repositories | **invert**: a provider interface in `common`, one implementation per module |
| `security/JwtClaimMapper` | `system.repository.SysUserRepository` | **invert**: a revocation-check interface in `common`, implemented in `system` |

**Two of these are cycles, not just odd shapes.** `module/system/controller/*` imports `security.LoginUser`, so `security → system` closes a loop that already exists in the other direction; likewise `AppointmentService`, `PrescriptionService` and `BillService` import `common.security.DoctorPatientScope`, so `common ↔ appointment` and `common ↔ prescription` are loops too. The other three are one-way reverse dependencies.

**Why the two cyclic ones are inverted rather than relocated:**

- `DoctorPatientScope` cannot move into `module/patient`: it needs appointment and prescription repositories, which from `patient` is a reverse dependency again, and it cannot call those modules' *services* because those services inject it — a bean cycle.
- `JwtClaimMapper` cannot move into `module/system`: `common/config/SecurityConfig` wires it as the JWT converter, so the edge would simply reappear from the other side.

**Decisions:**

1. The two schedulers go into each module's existing `service/` package rather than a new `job/` package, so "every module has the identical internal layout" keeps holding. `common/job/` keeps `DataRetentionJob`, which is genuinely cross-cutting: it archives the audit table `common` owns.
2. `app.retention.soft-delete-days` is **deleted, not implemented.** Nothing reads it, and API-LAYOUT plus `backend-architecture-explained.md` currently describe a soft-delete purge policy that does not exist ("soft-deleted records are retained 365 days before permanent removal"). Deleting a patient's medical rows on a timer is a compliance decision, not a cleanup task, so the honest fix is to delete the knob and correct the two documents. ROADMAP's Round 47 review had already flagged the same job as half-implemented.
3. Inversion shape: the provider returns the patient ids a doctor is related to through that module's records; `DoctorPatientScope` unions all providers plus the break-glass patient, so `requireAccess` semantics are untouched (null = ADMIN, unscoped; empty set = no patients).

**What landed**

| # | Change |
|---|--------|
| M8.5.1 | `AppointmentScheduler` → `module/appointment/service/`, `QualityScheduler` → `module/quality/service/` (same class names, same crons, same `ApplicationReadyEvent` listener) |
| M8.5.2 | `DataRetentionJob` lost five never-read repository injections and the never-read `app.retention.soft-delete-days`; it is now a `common`-only job, which is what it always was |
| M8.5.3 | `DoctorPatientScopeProvider` (in `common/security`) + `AppointmentScopeProvider` and `PrescriptionScopeProvider`; `DoctorPatientScope` unions the providers instead of querying two foreign repositories |
| M8.5.4 | `AccountRevocationCheck` (in `common/security`) + `SysUserRevocationCheck` (in `module/system`); `security/JwtClaimMapper` no longer reads `SysUserRepository` |
| M8.5.5 | `LayeringGuardTest`: a source scan that fails the build when any `common/` or `security/` file imports a business module |

**Verification**

| Check | Result |
|-------|--------|
| `grep -rn "import com.example.medical.module." common/ security/` | **empty** — the end state this slice promised |
| `mvn clean verify` | **178 tests, 0 failures** (176 → 178), enforcer clean |
| Relocated scheduler still runs (live, after restart) | `c.e.m.m.a.s.AppointmentScheduler - Running no-show check on startup` |
| Provider union still enforces scope (live, doctor1 token) | in scope `/patients/100/vitals` **200**; out of scope `/patients/102/vitals` **403** `Access denied: patient outside your scope` |
| Inverted revocation seam (live) | token issued before the account was disabled → **401**, and the branch now has its first test |
| Portal unaffected (live) | vitals / bills / appointments / prescriptions 200; export still 6 sections, 4/2/2 rows |

### M8.5.6 — two defects the new tests surfaced ✅

**1. `@ValidPassword` rejected "no password", so every staff-account edit demanded one.** `PasswordPolicyValidator.isValid` returned false for null *and* blank, which made the optional field on `SysUserUpdateFormDTO` mandatory — and `SysUserService.update` rotates the password whenever one is supplied, so an admin fixing a typo in a phone number silently reset that user's credentials and wrote a password-history row. `SystemIntegrationTest` had even recorded the accident as intentional: *"Backend requires @NotBlank password even on update — this is a known issue"*. The validator now ignores absent/blank values (presence stays the caller's job, and all four required sites carry `@NotBlank`), the DTO's documented "blank keeps the current password" is real, and that test was rewritten to prove the credentials survive an edit.

**2. A revoked token threw instead of failing authentication.** `JwtClaimMapper` rejected a stale token with `JwtValidationException`, which is not an `AuthenticationException` — so `BearerTokenAuthenticationFilter` did not catch it and the exception escaped to the DispatcherServlet. Measured on the running server before the fix: the client still saw 401 (by accident, through the container's error path), but each request appended **194 log lines including an ERROR stack trace**, and the `WWW-Authenticate` header carried no reason at all. It now throws `OAuth2AuthenticationException` with an `OAuth2Error`, which the filter answers cleanly: **401**, `error_description="Account was disabled or credentials changed after token issuance"`, and **10 log lines, 0 errors**. The same change covers the refresh-token-as-access-token rejection in the same class, which had the identical flaw.

Neither defect was in this slice's scope; both were in code the slice had to touch, and both are now covered by tests that would fail if the behaviour regressed.

**Lesson (workflow) — a live probe is only valid on an instance started after the last build.** `mvn spring-boot:run` puts `target/classes`, a *directory*, on the runtime classpath, so the JVM reads classes lazily from disk: bytes already loaded are frozen, while a class first touched after a rebuild is read fresh. Rebuilding under a live instance therefore yields a half-old/half-new process whose behaviour depends on load timing rather than on the code on disk. This is how defect 2 below surfaced: a probe answered the *uncommitted* version of `PasswordPolicyValidator` on a process that had started before that version existed.

It was then proved in an isolated copy (never touching the working tree): with the validator rewritten and recompiled under a running process P1, the same request that HEAD answers **200** came back **400**; recompiling the original bytes back did **not** restore P1's behaviour (loaded classes are sticky); and a fresh process P2 started from those identical bytes answered **200** again — one source tree, two answers, the only difference being when the class was first loaded. The first probe written for this experiment was confounded (`PUT /users/me/password` also carries `@NotBlank`, so blank was rejected in both worlds); the discriminating field is the one `@ValidPassword` site *without* `@NotBlank`, `SysUserUpdateFormDTO.password`. The control process is what caught that.

Rule now recorded in CLAUDE.md §8b: **edit → stop the instance → `mvn clean verify` → start → probe**, no build while probing, confirm the instance is the one just started (boot-log line + port), and note that `clean` deletes `target/` under a running process — which turns a later first-load into a `ClassNotFoundException` instead of a stale read.

**Files:** `common/job/{AppointmentScheduler,QualityScheduler}.java` (moved into `module/*/service/`), `common/job/DataRetentionJob.java`, `common/security/DoctorPatientScope.java` + `DoctorPatientScopeProvider.java` (new), `module/appointment/service/AppointmentScopeProvider.java` (new), `module/prescription/service/PrescriptionScopeProvider.java` (new), `common/security/AccountRevocationCheck.java` (new), `module/system/service/SysUserRevocationCheck.java` (new), `security/JwtClaimMapper.java`, `application.yml`, `docs/API-LAYOUT.md`, `docs/backend-architecture-explained.md`, `AuthIntegrationTest.java`, `LayeringGuardTest.java` (new).

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
| ~~`ExportController` → `PatientRepository`, `BillRepository`~~ | **Closed in Round 51.9** — the reads moved to `PatientService.exportPage` / `BillService.exportPage` and the CSVs came out byte-identical |
| `EmergencyAccessController` → `PatientRepository` | It only needs "does this patient exist"; the honest fix is a `PatientService` existence method, which is a `system → patient` service edge — M8.5's territory. **The last one left** |

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
| M8.5 ✅ | Remove `common → module` reverse deps — **measured at five files** (2 schedulers relocated, 1 job whose cross-module half was dead code, 2 inverted because relocating them would recreate the cycle); `DataRetentionJob` stays in `common` because archiving the audit table is genuinely cross-cutting. `LayeringGuardTest` makes the end state permanent, and the slice's tests surfaced two more defects | `common/job/*`; `common/security/*`; `security/JwtClaimMapper`; affected module packages |
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

## Round 51 — UI-pass follow-ups (2026-09-17)

The first real browser pass over the post-M8 tree — headless Chrome driven over CDP, 33 pages, both logins, a bill created through the form, the export downloaded — came back clean: **0 console errors, 0 uncaught exceptions, 0 API responses ≥ 400, no horizontal overflow anywhere**, and the M8-specific changes were visible exactly as intended (a DRAFT bill with no *Pay Now* next to a PENDING one that has it; the export downloading a 6-section document). Four issues did surface; three are fixed here, and the two cosmetic ones plus one navigation gap are left open on purpose.

| # | Finding | Fix |
|---|---------|-----|
| 51.1 | **`PUT /charges/{id}/convert` billed the same visit twice.** It created a bill without checking whether the appointment already had one, and never copied `appointmentId` onto the new bill. Reachable in two clicks: the Billing page's "Draft Charges — Ready to Convert (2)" box listed exactly the two seeded charges whose appointments already carried paid bills 500/502 | convert answers **409 `Appointment 201 is already billed (bill 500)`** when the appointment carries any bill, and copies `appointmentId` onto the new bill. The seeded charges for appointments 201/204 are now `BILLED` with `bill_id` 500/502, so a fresh database no longer advertises a conversion that would duplicate |
| 51.2 | **An unknown URL rendered a completely blank page** — no shell, no sidebar, no message, nothing to click. `App.tsx` had no catch-all route | New `views/NotFound.tsx` plus a `path="*"` child inside **both** layouts, so the shell survives and the page names the path and offers a link home |
| 51.3 | **A dev server on any port other than 5173 could not log in.** The backend's origin allowlist is a single value, so every login answered `403 Invalid CORS request` and the UI showed only a generic failure — easy to misread as a backend fault (measured directly: `Origin: http://localhost:5173` → 200, `Origin: http://localhost:5174` → 403, no `Origin` header → 200) | README documents the constraint and the `CORS_ORIGINS` override |

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **180 tests, 0 failures** (178 → 180: two convert cases in `BillingIntegrationTest`), enforcer clean |
| 51.1, live on a fresh database | seed shows charges `BILLED` (bill_id 500/502); a DRAFT charge for the billed appointment 201 → **409 `Appointment 201 is already billed (bill 500)`**; a charge for the unbilled appointment 203 → **200**, bill carries `appointmentId=203`, status DRAFT; converting that charge again → 409 `Charge is not in DRAFT status` |
| 51.2, in the browser | `/nope-does-not-exist`, `/system/nope` and (logged in) `/patient/nope` all render the shell plus "Page not found … Go back to …"; logged-out `/patient/nope` redirects to the patient login |
| Routing regression after adding `path="*"` | **all 33 pages re-swept**: every one renders, zero horizontal overflow, 0 console errors, 0 API responses ≥ 400 |
| Frontend checks | tsc clean; eslint 0 errors, the same 4 pre-existing warnings |

### Round 51.4 — the Lab flag vocabulary disagreed with its own column ✅

The bare "A" in the Lab results legend turned out to be the visible tip of a four-layer disagreement, and one layer was a **500 on a live ingest path**:

| Layer | What it said |
|-------|--------------|
| Storage | `abnormal_flag CHAR(1)` (`schema.sql`) — room for exactly one character |
| Intake | `LabResultService` copied the external flag through untouched |
| FHIR mapper | tested `H` **or `HH`**, `L` or `LL` — assuming two-character values exist |
| Frontend | colours for `N/H/L/HH/LL/A`, but the legend labelled only four of them and left `A` bare |
| Seed data | only ever wrote `H` and `N`, which is why nothing had failed yet |

Measured through `POST /api/v1/integration/lab-results` (isolated instance, throwaway DB): `H`, `N`, `A` → **200 ACK**; `HH` and `LL` → **500**, `Value too long for column "ABNORMAL_FLAG CHARACTER(1)"` (H2 `SQLState 22001`). One critical-value message from an interface engine took the whole ingest down.

Fixed by **folding the vocabulary down to what the column can hold**, at the intake: `HH/HU → H`, `LL/LU → L` (case-insensitive), `N` and `A` kept, anything unrecognised → null rather than a guess. The frontend legend and colour map now describe only storable values (`A Abnormal`, `H High / L Low`, `N Normal`) and the FHIR mapper's `HH`/`LL` branches — unreachable once nothing two-character is stored — are gone. Widening the column to `VARCHAR(2)` was rejected: it would need a schema change and therefore a local database rebuild, for a distinction that normalising at the boundary preserves better.

Also fixed here, after one wrong first attempt: read-only fields in the patient profile (six of eighteen — name, MRN, date of birth, sex at birth, insurance payer, allergies) are staff-maintained and looked exactly like the editable ones, so they now render with a grey background, muted text and a `not-allowed` cursor.

The first attempt put that rule on every disabled control in a form grid — `.formGroup input:disabled, select:disabled, textarea:disabled` in `views/shared.module.css` — and that sheet is **shared with the staff views**, whose read-only forms also use `.formGroup`. Measured blast radius: the staff patient view (26 disabled inputs in the View Patient modal alone), the appointment view for terminal appointments (12 controls) and the prescription view (14 controls) all changed appearance, which nobody asked for. Narrowed to a `.readonlyField` class applied only by the patient profile. Verified after narrowing: the patient page still shows 6 fields at `rgb(245,247,250)` / `rgb(144,147,153)` / `not-allowed` against 12 white editable ones, while the staff patient view is back to the browser default (`rgb(84,84,84)` text, default cursor) and the appointment view to a white background — i.e. exactly their state before the change.

**Nothing from this pass is left open.** Of the three items originally listed here, two are fixed in 51.4 above (the legend, the read-only styling) and the third was **wrong**:

> "the patient sidebar lists 7 destinations while the router defines 15, leaving vitals, problems, immunizations, care-plans, referrals, prior-auths, consent and disclosures reachable only by typing the URL"

The same browser pass disproved it: the patient Dashboard carries three card groups (OVERVIEW / HEALTH RECORDS / ACCOUNT) linking to **all 15** portal pages, so nothing is unreachable. The sidebar was inspected and the dashboard was not, even though its screenshot was already in hand — the lesson is to trace the actual navigation graph (or open the screenshot) before claiming something cannot be reached. Deliberately unchanged: a 15-item sidebar would duplicate the cards, which *are* the portal's navigation.

### Round 51.5 — documentation audit against the code ✅

Prompted by "are the docs in sync?", every claim touched by Rounds 50–51 was re-grepped against the code rather than trusted. What the audit found:

| # | Drift | Fix |
|---|-------|-----|
| 51.5.1 | `API-LAYOUT.md`'s `PUT /charges/{id}/convert` row still described only "Non-DRAFT → 409" | Documents the second 409 (appointment already billed, with the bill id in the message) and that `appointmentId` is copied onto the new bill |
| 51.5.2 | Neither `API-LAYOUT.md` nor `MIRTH-CONNECT-INTEGRATION.md` mentioned that the lab intake **folds** the flag vocabulary | Both now state the accepted HL7 set, the normalisation, and that the column is `CHAR(1)` (the 500 this used to cause) |
| 51.5.3 | `backend-architecture-explained.md` presented `LabAnalysisService.autoFlag()` as a working feature | It **has no caller** and returns two-character levels the column cannot store. The doc now says so and the ROADMAP's Round 7.2 row points at it — wiring or deleting it is an open decision, listed below |
| 51.5.4 | Password docs described the complexity policy as unconditional | Noted that it applies to a **supplied** password, and that an absent/blank one on the optional field means "keep" (M8.5's fix) |
| 51.5.5 | `API-LAYOUT.md` placed `DoctorPatientScope` correctly but not how it reaches module data | Notes the per-module `DoctorPatientScopeProvider` that keeps `common` free of module imports (M8.5) |
| 51.5.6 | **My own miss from 51.4**: the patient portal's lab page carries a *second* copy of the flag colour map and legend, so fixing the staff page left `/patient/lab` showing the old `HH/LL Critical … A` legend | Both pages now read `ABNORMAL_FLAG_COLOR` / `ABNORMAL_FLAG_LEGEND` from `utils/labels.ts`, where this project's other colour maps already live — the duplication is what made the first fix incomplete, so it is gone rather than copied a third time |

**Verification:** frontend `npm run check` clean (tsc + eslint, same 4 pre-existing warnings); both lab pages re-checked in the browser and now render the identical legend; `mvn clean verify` unaffected (no backend change in this slice) — the last full run stands at 181 green.

### Round 51.6 — the two open decisions closed ✅

**1. `LabAnalysisService.autoFlag()` deleted.** It had no caller anywhere in the codebase, and the five levels it returned (`LL`/`L`/`H`/`HH`/`N`) could not be stored in `abnormal_flag CHAR(1)` — it was documented as a feature while being unreachable code that could only have produced the same 500 the intake used to. Removing it also removed its only dependency (`LoincCatalogRepository`). Round 7.2's row now records that the feature was never wired; if derived flags are ever wanted, the intake is the place (it already folds HL7's two-character levels).

**2. The two lab pages now share one implementation.** `views/lab/LabResults.tsx` and `views/patient/lab/index.tsx` each carried their own copy of the date grouping, the trend calculation, the trend banner, the results table, the pagination and the flag legend — ~70 lines twice over, and the duplication had already caused a miss (51.5.6: only one page's legend got fixed). Both now render `views/lab/LabResultsTable.tsx`, which owns the grouping, the trend arrow, the table and the pager, and exports `LabFlagLegend` and `LAB_PAGE_SIZE`. The pages keep only what differs: where the data comes from (staff selects a patient, the portal reads its own token), the heading and the empty-message wording. `LabResults.tsx` 181 → 92 lines, `patient/lab/index.tsx` 154 → 66, component 154.

| Check | Result |
|-------|--------|
| `mvn clean verify` | **181 tests, 0 failures** — unchanged by the dead-code removal |
| `npm run check` | tsc clean; eslint 0 errors (it caught the three imports the extraction orphaned) |
| Staff `/lab`, patient selected | 20 rows, `Total: 30 … Page 1`, dates grouped, legend rendered |
| Staff `/lab`, **Next** | page 2, 10 rows, first group `2026-03-20` — the moved `onPageChange` wiring works |
| Staff `/lab` with no patient | no rows and no empty message (`emptyMessage` is suppressed rather than shown) |
| Both pages, filter = Glucose | trend banner with 4 points and a `↓` arrow — page 1 pager correctly hidden in trend mode |
| Browser console / network | 0 console errors, 0 responses ≥ 400 across the whole walk |

### Round 51.7 — the dashboard counted patients the caller cannot open ✅

Reported from the UI: the overview card said **Total Patients 4** while the patient list showed **2**. Reproduced per role:

| Caller | `/dashboard/stats` → `totalPatients` | `/patients` → `total` |
|--------|--------------------------------------|----------------------|
| admin | 4 | 4 |
| doctor1 | **4** | **2** |

Root cause: `DashboardService` is the one aggregate built on raw `JdbcTemplate` rather than JPA, so it inherits neither `@SQLRestriction` nor `DoctorPatientScope` — every counter was practice-wide regardless of who asked, while every list the same user can open is doctor-scoped. The existing test only asserted `200` for a doctor, which is why the numbers were never compared.

A second defect sat behind it: `@Cacheable(value = "dashboard", key = "'stats'")` used a **constant** key, so one caller's stats were served to everybody. It is invisible in the h2 profile (`spring.cache.type: none`) and would have kept the doctor's card at the practice-wide number under Redis even after the scoping fix — the key now carries the scope (`stats:ALL` / `stats:[100, 101]`).

**Fix:** every patient-scoped query in `DashboardService` now appends a scope filter (`patient_id IN (…)`, `id IN (…)` for the patient table itself), with an explicit `AND 1 = 0` for an empty scope — "no patients" must mean zero rows, and `IN ()` is not valid SQL. The cache key is derived from the resolved scope (`scopeKey()`), for the same reason.

**Verification**

| Check | Result |
|-------|--------|
| Live, admin | `totalPatients=4` and `/patients total=4`; `pendingBills=1` and `/bills?claimStatus=PENDING total=1`; `scheduledAppointments=0` and `/appointments?status=0 total=0` |
| Live, doctor1 | `totalPatients=**2**` and `/patients total=2` (was 4 vs 2) |
| In the UI, doctor1 | Dashboard card reads **2** and the patient list holds exactly 2 rows (100, 101) — the path the report came from |
| New test | `dashboardStats_shouldAgreeWithTheListsForTheSameCaller` asserts the three comparable counters against the corresponding list endpoint **for both roles**, so this class of bug fails the build from now on |
| `mvn clean verify` | **182 tests, 0 failures** (181 → 182) |

**Not verified locally:** the Redis-backed cache path. The h2 profile sets `spring.cache.type: none`, so `@Cacheable` is inert in every local run; the scope-aware key is by inspection, and no Redis is available here to exercise it.

### Round 51.8 — the last eight untyped responses are VOs ✅

M8.1 promised "no endpoint returns a raw entity"; the remaining gap was the other half of the same idea — endpoints returning `Map<String, Object>`, where nothing states the shape. Eight of them, all now typed, **with the wire unchanged**:

| Endpoint | Was | Now |
|----------|-----|-----|
| `POST /integration/adt` | `Map.of("status","ACK","sourceMessageId",…)` | `IntegrationAckVO` `{status, sourceMessageId}` |
| `POST /integration/lab-results` | `Map.of(…, "recordsCreated", count)` | `IntegrationAckVO` `{status, sourceMessageId, recordsCreated}` |
| `PUT /prescriptions/{id}/transmit` | 4-key map | `TransmitResultVO` `{status, format, messageId, xml}` |
| `GET /formulary/check` (hit / miss) | 6-key map / 2-key map | `FormularyCheckVO` (one class, `@JsonInclude(NON_NULL)` so each branch keeps exactly its own keys) |
| `GET /users/doctors` | `List<Map>` per row | `DoctorOptionVO[]` `{id, username, realName}` |
| `GET /audit-logs/verify` | 2-key map | `IntegrityReportVO` `{intact, brokenRowId}` |
| `GET` + `POST /quality/measures/{cmsId}/report|calculate` | `Map` built in the service | `QualityReportVO`, the same 10 keys from both |

**Two contract defects surfaced while typing them** — both invisible until something had to name the fields:

1. **`calculate` returned a different `title` than `report`.** The read-back used `measure.getTitle()`; the calculate path put `getTarget(cmsId)` — the *target description sentence* — into the same key, and the real target into `performanceTarget`. So after a calculation the two endpoints disagreed about what `title` means (the UI masked it by re-fetching the read-back afterwards). Both now answer with the measure's own title, and the description stays in `performanceTarget`.
2. **The TypeScript type lied about `performanceTarget`.** `api/quality.ts` typed both report endpoints as `QualityResultVO`, whose `performanceTarget` was `number` while the API has always sent a string — invisible because the page renders it as a paragraph. There is now a `QualityReportVO` interface with truthful types.

Writing the interface also caught my own mistake immediately: I first marked the five counters optional, and `tsc` rejected the page that feeds them to number-typed helpers. They are always present (the report falls back to 0 rather than omitting a counter), so the type says required — the compiler did the review.

**Verification** — every endpoint called live and its key set compared with the shape it had before the change:

| Call | Keys returned |
|------|---------------|
| `GET /users/doctors` | `id, realName, username` — as before |
| `GET /audit-logs/verify` | `brokenRowId, intact` — as before |
| `GET /quality/measures/CMS122v11/report` | the same 10 keys |
| `POST /quality/measure/CMS122v11/calculate` | same 10 keys, `title = 'HbA1c Poor Control (>9%)'` — matching the read-back (the fix above) |
| `GET /formulary/check` hit | `alternatives, drugName, found, priorAuthRequired, stepTherapyRequired, tier` — as before |
| `GET /formulary/check` miss | `found, message` — as before, the other five keys omitted |
| `POST /integration/adt` | `sourceMessageId, status` — no `recordsCreated`, as before |
| `POST /integration/lab-results` | `recordsCreated, sourceMessageId, status` — as before |
| `PUT /prescriptions/301/transmit` | `format, messageId, status, xml` — as before (301 is `active`; 300 answers 409 `Only active prescriptions can be transmitted`, a business rule) |

Read-only calls were made against the dev instance, the three mutating ones against a throwaway database. `mvn clean verify` **182 tests green**, `npm run check` clean, and the eCQM page re-checked in the browser (heading `HbA1c Poor Control (>9%)`, target paragraph, four metrics, 0 console errors).

**Correction (Round 51.12):** the `PUT /prescriptions/{id}/transmit` row above is wrong — `TransmitResultVO` was *created* here but the controller kept building its four-key `Map.of(...)`, so only seven of the eight were actually typed and the class was dead code until 51.12 wired it. The live check in this table could not catch it because the map carried exactly the same four keys.

**Still open from this slice:** `tier` on the formulary is a **String** holding "1"/"2" (seed values), which a real formulary would model as an int or an enum; and `api/formulary.ts` + `/formulary/check` have no UI caller at all — deleting both is still on the table rather than typing an endpoint nothing uses.

### Round 51.9 — the export stops reaching across modules ✅

`ExportController` was the last *complex* case: 156 lines that paginated `PatientRepository` and `BillRepository` inside a `StreamingResponseBody`, filtering by doctor scope **in memory** while walking the whole table.

| Change | Detail |
|--------|--------|
| `PatientService.exportPage(page, size, scopedPatientIds)` / `BillService.exportPage(...)` | The owning services expose a paged read. It deliberately does **not** go through `Pages`: the export picks its own 500-row page size, which exceeds the 200 cap that exists for user-facing pages (the M7 note already recorded that exception) |
| Scope applied in SQL | `id IN (…)` / `patient_id IN (…)` instead of skipping rows after fetching them. An **empty** scope returns an empty page — never an unfiltered one |
| CSV shape stays in the export module | Header, row order, `csv()` escaping and the phone/email/claim-number masking are the export's business; it now reads `PatientVO` / `BillVO` instead of entities, which also means the streaming thread no longer touches a persistence context |

**Verification**

| Check | Result |
|-------|--------|
| `/export/patients`, before vs after | **byte-identical** — 1115 bytes, `md5 45e48284cd34729384931c0afa126420` |
| `/export/bills`, before vs after | **byte-identical** — 642 bytes, `md5 b108f74e4c181db40a5c83c89c5ab73c` |
| Doctor scope (live) | doctor1: 3 lines (header + `MRN-10001`, `MRN-10002`); admin: 5 lines (all four patients). Bill rows carry only patient ids `100`, `101` |
| Audit | `EXPORT_PATIENTS` / `EXPORT_BILLS` rows still written for both roles |
| Cross-module repositories left in controllers (grep) | **3 across 2 → 1 across 1** (`EmergencyAccessController` only) |
| `mvn clean verify` | **183 tests, 0 failures** (182 → 183) |

**The new test exposed that the two existing export tests were blind.** They asserted "not 500" on the first MockMvc result, but `StreamingResponseBody` starts async processing — the body only exists after an async dispatch, so `getContentAsString()` was returning an empty string all along and they could never have noticed a wrong CSV. The new test dispatches first, then compares the doctor's rows against the admin export and against the doctor's own patient-list total.

**One deliberate behaviour change on a degenerate input:** the claim-number column now comes from `BillVO.insuranceClaimNumberLast4`, whose masking returns `****` for values of four characters or fewer, where the old inline expression would print `****123` (i.e. reveal the whole short identifier). No such value exists in the data, and the new behaviour is the more private one, but it is a difference rather than a byte-for-byte match.

### Round 51.10 — the dead formulary endpoint is gone ✅

Round 51.8 typed `GET /api/v1/formulary/check` rather than deleting it, and flagged the reason it might not deserve typing: the only caller was `medical-web/src/api/formulary.ts`, which **no view imported**. Decision taken: remove the surface instead of describing it. Deleted:

- `FormularyController.check` — the endpoint and its two response branches
- `FormularyCheckVO` — created in 51.8, used by nothing else
- `FormularyEntryRepository.findByRxnormCodeAndInsurancePayer` — its only caller was the endpoint above (the surviving `findByRxnormCode` serves the list endpoint)
- `medical-web/src/api/formulary.ts` — the dead client, including its `FormularyCheckResult` interface

Verified by grep before deleting: no test called the endpoint, no view imported the client, and the repository method had exactly one caller. The compiled controller now contains no `formulary/check` mapping — though a stale caller does **not** get a 404: the sibling `GET /formulary/{rxnormCode}` matches the segment, so `/formulary/check?…` answers `200 {"data":[]}` (an empty lookup for the code "check"). Documented in API-LAYOUT rather than papered over.

The restart itself surfaced a second, unrelated annoyance worth writing down: stopping the backend and starting it again too quickly fails with `The file is locked: …/medical_dev.mv.db`, because the H2 file lock outlives the JVM by a few seconds. Hit twice while verifying this slice; the README's quick start now says so. Endpoint count 149 → 148 (`mvn clean verify` **183 tests green**, `npm run check` clean) — nothing was covering it, which is itself part of why it should not have been kept.

**Still standing, and now visibly so:** the rest of the formulary read surface is also uncalled from the UI — `GET /api/v1/formulary/{rxnormCode}` (`FormularyEntryVO`), the client-side `FormularyEntry` interface in `types/entities.ts`, and the 9 seeded `formulary_entry` rows. Unlike `/check`, that endpoint is a plain read of seeded reference data, so it is reported rather than removed: deleting the whole feature (endpoint, entity, seed, `cds` fallback) is a product decision, not cleanup.

### Round 51.11 — claim statuses are named (F4, first of four) ✅

The status strings in the billing lifecycle were the ones that had already caused a real defect: the UI offered "Pay Now" for a `DRAFT` bill while the service accepted only `PENDING`, because both sides wrote the same five strings independently. `BillClaimStatus` (DRAFT / SUBMITTED / PENDING / PAID / DENIED, with `value()`, `matches()`, `anyOf()`, `isTerminal()`, `isPayable()`, `of()` and a `parse()` that rejects unknown values) is now the only place they are written down — the same shape `AppointmentStatus` has had since M5.

| Site | Change |
|------|--------|
| `BillService` | 8 literals → enum calls: `submitClaim` (DRAFT only), `adjudicate` (SUBMITTED/PENDING only, lands PENDING or PAID), `applyPayment` (`isPayable`), `denyClaim` (`isTerminal`) |
| `ChargeService`, `BillFormDTO` | newly created bills take `BillClaimStatus.DRAFT.value()` |
| `DashboardService` | the three raw-SQL counters (`claim_status = 'PAID'` / `'PENDING'`) are built from the enum, so a rename cannot leave the dashboard quietly counting nothing |
| `BillController` | `?claimStatus=` is parsed through the enum: an unknown value is now **400 `Unknown claim status: PENDNG`** instead of 200 with an empty list |

**Nothing changes on the wire or in the database** — the column stays `VARCHAR`, the payload keeps the same strings, the frontend is untouched — which is the point: this is de-risking, not behaviour.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **187 tests, 0 failures** (183 → 187: 3 new `BillClaimStatusTest` cases + the 400 integration case), enforcer clean |
| Lifecycle, live on a throwaway database | create `DRAFT` (resp 80.00) → `submit` `SUBMITTED` → `adjudicate` `PENDING` (resp 50.00) → pay 10 stays `PENDING` → pay 40 **`PAID`** → paying again 409 `Bill must be in PENDING state to accept payment` → denying 409 `Cannot deny a bill that is already paid or denied`; a second bill went `SUBMITTED` → `DENIED` |
| Charge conversion | convert on an unbilled appointment produced a bill in `DRAFT`; converting an already-billed appointment still answers 409 (Round 51.1's guard) |
| Dashboard counters after the SQL change | `pendingBills=1`, `monthlyRevenue=0` — identical to the pre-change reading |
| Dev instance, after restart | `?claimStatus=PAID` → 2 rows; `?claimStatus=PENDNG` → **400**; dashboard unchanged |
| Unit tests | `onlyPendingIsPayable`, `paidAndDeniedAreTerminal`, `unknownStatusIsRejected` (including `of("paid")` → null: the wire values are upper case) |

**A verification mistake worth recording:** the first lifecycle run reported the bill stuck at `PENDING` after a full payment, and I nearly filed it as a regression. It was my own payload — the adjudicate request takes `adjustment`, not `insuranceAdjustment`, so both numbers were silently treated as zero, the patient responsibility stayed at 80.00 and 50.00 paid was simply not enough. The rule here: when a live check disagrees with the code, check the request shape before believing the finding.

**Remaining in F4** (next commits): `prescription.rx_status` (lower-case vocabulary, and the update path currently stores whatever the client sends), `charge.status` (DRAFT/BILLED), then `sys_user.status` (0/1 integers wired into force-logout and the login rejection) — plus the purely presentational statuses (consent, care plan, problem, referral, prior auth, refill, patient) that sit outside F4.

### Round 51.12 — prescription statuses are named (F4, second of four) ✅

The second F4 vocabulary, and the one with the worse failure mode: `prescription.rx_status` was **stored exactly as the client sent it** (`p.setRxStatus(dto.getRxStatus())`), while every guard in the codebase compared it against `"active"`. A client that sent `actve`, or guessed a value from the UI's own dropdown (`transmitted`, `dispensed`), produced a row that was neither active nor terminal — invisible to transmit, cancel and refill at once, and impossible to list as "finished". `PrescriptionRxStatus` (`active` / `generated` / `cancelled` / `completed`) is now the single vocabulary, in the shape `BillClaimStatus` (51.11) and `AppointmentStatus` already have: `value()`, `matches()`, `anyOf()`, `isActive()`, `isTerminal()`, `of()` and `parse()`.

| Site | Change |
|------|--------|
| `PrescriptionService.create` | stored whatever arrived → `PrescriptionRxStatus.parse(...)`, and the default is `ACTIVE.value()`. Unknown values are now **400 `Unknown prescription status: actve`** |
| `PrescriptionService.cancel` | `!"active".equals(...)` → `!isActive(...)`; the new status is `CANCELLED.value()` |
| `PrescriptionController.transmit` | `"active"` guard → `isActive(...)`; on success the row becomes `GENERATED.value()` instead of the literal `"generated"` |
| `RefillController.create` | the patient-side refill guard is `isActive(...)` rather than a string compare — the third copy of the same rule |
| `CdsService` | the active-medication query takes `ACTIVE.value()` |
| `medical-web` | `NON_DELETABLE_RX_STATUSES` in `utils/labels.ts` replaces the inline `['transmitted','dispensed','cancelled']`; the detail modal's read-only status dropdown (which offered the two invented values) is now plain text |

**The frontend's ghost list was not hypothetical, it was on screen.** The Del button hid for `transmitted`/`dispensed` — values no code path has ever written — and therefore *offered* deletion for `generated` and `completed`, while the detail modal displayed the same invented pair as the current status. Both are gone; `labels.ts` now says where the real values come from.

**A dead class found on the way.** `TransmitResultVO` was created in 51.8 (see the correction below) but never used: `transmit` still built its four-key `Map.of(...)` by hand. The endpoint now returns the VO — the JSON is unchanged, which is why 51.8's live check could not tell the difference.

**One rule for both enums.** 51.11's `BillClaimStatus` matched exactly, while this one trimmed and lower-cased inside `of()` — two answers to the same question. Both now split it the same way: **`of()` is exact** (it guards stored values, where a near-miss is a bug worth seeing) and **`parse()` is lenient** (it accepts what a caller types, then stores only the canonical value). `of("paid")` and `of("Completed")` are null; `parse(" paid ")` is `PAID` and `parse("  Active ")` stores `active`. `BillClaimStatusTest` and `PrescriptionRxStatusTest` both assert the split.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **191 tests, 0 failures** (187 → 191: 3 `PrescriptionRxStatusTest` cases + `createPrescription_unknownStatus_shouldReturn400`), enforcer clean |
| Create, live on a throwaway database | `rxStatus:"actve"` → **400 `Unknown prescription status: actve`**; `"dispensed"` → 400; absent → 200 `active`; `"  Active "` and `"ACTIVE"` → 200, **stored as `active`**. Row count after five calls: 7 = the 3 seeded + exactly the 4 accepted — the two rejected calls wrote nothing |
| Transmit | 303 (`active`) → 200 with exactly `{status, format, messageId, xml}`, `status:"generated"`; 300 (`completed`) → **409 `Only active prescriptions can be transmitted`** |
| Cancel | 304 (`active`) → 200; the same call again → **409 `Only active prescriptions can be cancelled`**; 300 (`completed`) → 409 |
| Refill (patient token) | prescription 300 → **409 `Only active prescriptions can be refilled`**; 301 → 200, request `PENDING` |
| Browser, `/prescriptions` | row 300 (`completed`) shows **no action buttons at all**; 301/302 (`active`) show Transmit / Cancel / Del. Screenshot `/tmp/ui-shots/93-rx-list-del-hidden.png` |
| Browser, "View Prescription" on 300 | Status reads `completed` as text — the dropdown with `Active/Transmitted/Dispensed/Cancelled` is gone. `/tmp/ui-shots/94-rx-detail-status.png`. 0 console errors, 0 failed API calls |
| 51.11's filter, re-checked after the rule change | `?claimStatus=PAID`, `=paid` and `= paid ` all → 200 with the same 2 rows; `=PENDNG` still → **400** |
| `npm run check` | clean (0 errors, the 4 pre-existing warnings) |

**Environment finding worth recording:** the first restart of the dev instance came up against a **read-only** database — `The database is read only` during `DataInitializer`, and a 500 on every login, with only one process holding the H2 file. The cause was not the app: this session's file sandbox permits writes inside the workspace, and the `h2` profile's database lives at `~/.medical-dev/data/medical_dev.mv.db`, outside it (`touch` there → `Operation not permitted`), so H2 fell back to read-only. Confirmed by contrast — the same bytes on a `/tmp` database accepted every write above. Nothing in the code changed for it; the dev instance simply has to be started with write access to its own data directory.

**Remaining in F4:** `charge.status` (DRAFT/BILLED) and `sys_user.status` (the 0/1 integers behind force-logout and the login rejection), plus the presentational statuses outside F4 (consent, care plan, problem, referral, prior auth, refill, patient).

### Round 51.13 — charge statuses are named (F4, third of four) ✅

`charge.status` is the smallest vocabulary in F4 — two values — and it was written in five places: the entity's field default, `ChargeService` twice on the convert path, `AppointmentService` when an appointment is completed, and the seed. `ChargeStatus` (DRAFT / BILLED) now owns them, with `value()`, `matches()`, `of()` and `isConvertible()`.

**It deliberately has no `parse()`, and that is the point.** Unlike claim and prescription statuses, no client ever sends this value: `ChargeForm` has no status field, creation always writes DRAFT, and the convert transition owns the other one. Adding a parser "for symmetry" would be dead code — exactly the trap Round 51.12 recorded when it found `TransmitResultVO` sitting unused since 51.8.

| Site | Change |
|------|--------|
| `Charge.status` | field default `"DRAFT"` → `ChargeStatus.DRAFT.value()` |
| `ChargeService.create` | `"DRAFT"` → `ChargeStatus.DRAFT.value()` |
| `ChargeService.convert` | the guard is `!ChargeStatus.isConvertible(...)`, and the 409 message is built from the enum — `Charge is not in DRAFT status` cannot drift from the value it checks |
| `ChargeService.convert` | after billing, `"BILLED"` → `ChargeStatus.BILLED.value()` |
| `AppointmentService` | the charge it creates when a visit completes → the same constant |
| `medical-web` | `CONVERTIBLE_CHARGE_STATUSES` and `CHARGE_STATUS_COLOR` in `utils/labels.ts` replace the inline `'DRAFT'`/`'BILLED'` comparisons and the inline amber/green ternary in `views/charges`, plus the duplicated filter in `views/billing` |

**One literal is left on purpose:** `DataInitializer` still passes `"BILLED"` to its seed SQL, because `common/` may not import a module class — `LayeringGuardTest` fails the build for it, which is also why every other seed value there is a literal too. The comment beside it now says so.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **195 tests, 0 failures** (191 → 195: 3 `ChargeStatusTest` cases + `convertCharge_shouldRefuseWhenItIsAlreadyBilled`), enforcer clean |
| New integration case | a seeded BILLED charge → **409 `Charge is not in DRAFT status`** — the status guard, reached before 51.1's appointment guard |
| Existing convert cases | still green: a DRAFT charge for the billed appointment 201 → 409 `…already billed (bill 500)`; for appointment 203 → 200 with `appointmentId=203` |
| Browser, `/charges` on a fresh database | charge 3 (DRAFT) amber `rgb(230,162,60)` with a Convert to Bill button; charges 1/2 (BILLED) green `rgb(103,194,58)`, **no button**, `Bill #500` / `Bill #502`. Screenshot `/tmp/ui-shots/95-charges-status.png` |
| Browser, `/billing` | "Draft Charges — Ready to Convert (1)" lists exactly the one DRAFT charge. `/tmp/ui-shots/96-billing-draft-charges.png` |
| Browser errors | 0 console errors, 0 failed API calls on both pages |
| Test placement | `BillClaimStatusTest` declared `com.example.medical.module.billing.entity` while sitting in the root package directory — moved next to its enum, so all three status-enum tests now do. It was the only such mismatch in the 28 test files |

**The dev database is stale, and it looks like a bug.** Pointing the browser at the ordinary dev instance showed both seeded charges as DRAFT with a Convert to Bill button, even though appointments 201/204 carry bills 500/502 — the 51.1 seed change (charges billed, `bill_id` set) only runs against an **empty** database, and `~/.medical-dev/data/medical_dev.mv.db` predates it. Clicking Convert there answers 409 `Appointment 201 is already billed (bill 500)`, so nothing is double-billed; the states above were therefore verified on a fresh database. Resetting the dev database is the fix, and it is a deliberate act (it discards local audit logs and test records), so it is reported rather than done.

**Remaining in F4:** `sys_user.status` (the 0/1 integers behind force-logout and the login rejection). Outside F4, and now visible in one place: the frontend still spells out status values inline — `claimStatus === 'DRAFT'/'SUBMITTED'/'PENDING'` in `views/billing` and `views/patient/bills`, plus the referral, prior-auth and refill vocabularies. Those are the same defect class as 51.12's ghost dropdown, and a frontend pass over `utils/labels.ts` is the natural follow-up. Also left standing from M8.5: `AppointmentService` writes to `ChargeRepository` directly (a module→module repository edge, not covered by `LayeringGuardTest`) — charge creation belongs behind `ChargeService`.

### Round 51.14 — the enabled/disabled flag is named (F4, fourth of four) ✅

`0` and `1` behind `status` on **three** tables (`sys_user`, `sys_role`, `sys_menu`) decided two real behaviours — `AuthService` refuses a disabled account's login and `SysUserService` forces its sessions out when it is switched off — while the payloads stored whatever integer arrived. `EnabledStatus` (DISABLED 0 / ENABLED 1) now owns it, with `value()`, `matches()`, `of()`, `parse()`, `isEnabled()` and `isDisabled()`.

| Site | Change |
|------|--------|
| `SysUser` / `SysRole` / `SysMenu` | the field had no default, so a new instance was "neither state"; all three now start at `ENABLED` |
| `SysUserFormDTO.toEntity` | stored the raw `Integer` → `EnabledStatus.parse(status).value()`: **400 `Unknown status: 7`** for anything but 0/1, and an absent value now means ENABLED — the default the role and menu payloads already applied, while the user payload stored `null` |
| `SysRoleFormDTO` / `SysMenuFormDTO` `toEntity` | `status != null ? status : 1` → the same call |
| `SysUserUpdateFormDTO` / `SysRoleFormDTO` / `SysMenuFormDTO` `applyTo` | **an update that omits the status keeps it.** It used to null it, and `null` is not disabled as far as `AuthService` is concerned — so any partial update silently re-enabled a switched-off account |
| `AuthService.login` | `user.getStatus() != null && user.getStatus() == 0` → `EnabledStatus.isDisabled(...)` (the same 403 `Account is disabled`) |
| `SysUserService.update` | the force-logout branch (`oldStatus == 1 && newStatus == 0`) reads through the enum |

**The flag had no UI, and that is the finding.** `sys_user.status` was carried in the payload, never rendered and never editable: the only way to disable an account was a hand-written request, even though a disabled account is refused at login and force-logged-out mid-session. Worse, the users table's own **Status** column reads `lockedUntil` — the *lockout* state — so a disabled account was displayed as **Active**.

Both are fixed in the smallest way that makes the vocabulary reachable:

| File | Change |
|------|--------|
| `views/system/users` | the form gained a `status` select (Enabled / Disabled); the table's Status column now shows **Disabled** (red) before Locked/Active |
| `SysUserFormDTO.applyTo` | deleted — nothing called it (`SysUserService.update` uses `SysUserUpdateFormDTO.applyTo`); it was the second dead method found by this run of F4, after `TransmitResultVO` |

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **200 tests, 0 failures** (195 → 200: 3 `EnabledStatusTest` cases + 2 system integration cases), enforcer clean |
| Live, throwaway database | `status: 7` → **400 `Unknown status: 7`**; `status: 0` → 200 and that account's login → **403 `Account is disabled`**; no `status` at all → 200 and the account logs in |
| The mid-session path | a probe user's token answered **403** (role-less, so forbidden) before being disabled and **401** afterwards — `force_logout_after` reached it. A `PUT` carrying only `realName` left the status at 0 (still 403) |
| Browser, `/system/users` | the Add/Edit form has a `status` select (options `1:Enabled`, `0:Disabled`); creating `ui-disabled-probe` with **Disabled** stored `status = 0` and that account's login answered 403. Screenshots `/tmp/ui-shots/98-user-form-disabled-selected.png`, `99-users-status-column.png` |
| Browser, list column | admin and doctor1 show **Active** (green); the three probe accounts show **Disabled** (red, `rgb(245,108,108)`). Reopening a disabled user shows the select on Disabled. 0 console errors, 0 failed API calls |
| `npm run check` | clean (0 errors, the 4 pre-existing warnings) |

**F4 is complete** — four vocabularies, four commits (`claim_status`, `rx_status`, `charge.status`, the 0/1 flag), each one verified against a running instance. What it bought, concretely: two endpoints that answered 200 with a silently-empty result now answer 400, a create path that stored whatever it was sent now rejects it, an update path that could re-enable a disabled account no longer can, and a login refusal that had no test now has one.

**Left outside F4, deliberately:** the presentational vocabularies (consent, care plan, problem, referral, prior auth, refill request, `patient_auth.status`) and the frontend's inline status literals listed in 51.13. `sys_role.status` and `sys_menu.status` are now named but still enforced nowhere — nothing filters a disabled role or menu out of a login or a menu tree, which is a functional gap rather than a vocabulary one.

## Round 52 — module boundaries (complete)

> **Status: complete.** 52.1–52.8 landed (with 52.3a, 52.3b and 52.5a found by measurement along the way). Every metric is at zero and the allowlist mechanism is deleted. Written before the first slice, following the M8.4 precedent. Every number below is a measurement, not an impression; the tables in the plan half were corrected by 52.1's own findings, and 52.3 added two slices — one when its measurement found the guard blind to fully-qualified references, one for the write that blindness had hidden. The cycle target in the table below is met: the module graph is acyclic as of 52.4.

**The rule, and what breaks it.** CLAUDE.md says "No cyclic references between modules" and "JPA repositories live in each module's `repository/` directory". `LayeringGuardTest` (M8.5) enforces the first of those only for `common/` and `security/` — the shared kernel must not import `module.*`. Nothing looks at `module ↔ module`. Measured today:

| Metric | Now | Target |
|--------|-----|--------|
| module→module import pairs | 17 | unchanged — most are legitimate consumer edges |
| **import cycles** | **6** — four between two modules (`appointment↔patient`, `billing↔patient`, `patient↔prescription`, `patient↔system`) and two through three (`appointment↔system↔patient`, `patient↔prescription↔system`) | **0** |
| files importing **another module's repository** | **10 files, 17 import statements** | **0** |
| cross-module `entity` imports | **18 imports** — 16 entity classes, 2 enums (`BillClaimStatus`, `AppointmentStatus`) | 0 entity classes; the enums stay, they are shared vocabulary on purpose (F4 #1) |

**The plan's first numbers were low, and 52.1 is what corrected them.** The survey behind this section was a pairwise scan, which cannot see a cycle through three modules and missed four entity imports that are not paired with a repository import. The guard written in 52.1 found both on its first run — 4 → 6 cycles and 12 → 16 entity classes — before a single line of the refactor was written. That is the argument for landing the check first.

The ten files: `AppointmentService`, `BillService`, `ChatService`, `AdtService`, `LabResultService`, `PatientCaseService`, `CdsService`, `PrescriptionService`, `QualityMeasureService`, `EmergencyAccessController`.

Why this is worth a round rather than a style note: a repository **is** a module's persistence contract. Reaching into it from outside skips that module's rules — `PatientService.getById` audits the read (`@Auditable(module = "patient", action = "VIEW", phiAccess = true)`), and its list reads are scoped to the caller's patients; a raw `PatientRepository.findById` inside `BillService` does neither — and a cycle means neither module can be understood, tested or moved without the other. M8.4's own decision note deferred exactly this: *"Cycle governance belongs to M8.5/M8.6."*

### Decisions taken before any code

1. **Reads get an interface in `common/lookup/`; writes get the owning module's service.** Precedent: `common/security/AccountRevocationCheck`, added because `JwtClaimMapper` reading `SysUserRepository` closed a cycle, and `PatientService.exportPage` from 51.9. Two interfaces cover the read side: `PatientLookup` (`displayName`, `allergies`, `exists`) and `StaffLookup` (`realName`, `prescriberIdentity` → NPI + DEA). They must be **scope-free and audit-free** — they are display helpers; a lookup that starts answering 403, or writes an audit row per name resolved, is a behaviour change, not a refactor.
2. **The portal keeps calling other modules' services.** `patient → appointment/billing/prescription/system` stays: it is a consumer relation, and once decision 1 lands those directions are one-way. Moving the six portal controllers into their own module was considered and rejected for the same reason M8.4 rejected it.
3. **The guard lands first, with a shrinking allowlist.** The new `LayeringGuardTest` rules — no cross-module repository imports, no cross-module entity imports (enums excepted), no module cycles — arrive in 52.1 together with today's violations listed as `file → the slice that removes it`. The build stays green, a new violation is blocked from day one, and each slice deletes its own entries. 52.8 deletes the allowlist constant itself, so the mechanism cannot quietly become a permanent dumping ground.
4. **The quality slice is a data-shape decision, not a rename.** `QualityMeasureService` walks `patientRepository.findAll()` and reads decrypted demographics, conditions and the latest observation per LOINC across three eCQM measures. It needs a narrow clinical read model — the one slice where the interface has to be designed rather than discovered.

### Slice 52.1 ✅ — the guard, with an allowlist that can only shrink

`LayeringGuardTest` gained four checks next to the M8.5 one:

| Check | Rule |
|-------|------|
| `noModuleShouldImportAnotherModulesRepository` | a module may not import another module's `repository` package |
| `crossModuleEntityImportsShouldBeEnumsOnly` | a module may not import another module's entity — **enums are exempt**, decided by reading the target file for an `enum` declaration, because `BillClaimStatus` and `AppointmentStatus` are vocabularies the neighbours legitimately share |
| `modulesShouldNotFormImportCycles` | depth-first walk of the module import graph; reports every cycle, at any length, rotated so the alphabetically first module leads |
| `allowlistsShouldNotContainFixedEntries` | every allowlisted violation must still exist |

The third rule is what made 52.1 worth doing first: it found **two cycles through three modules** (`appointment↔system↔patient`, `patient↔prescription↔system`) that the pairwise survey behind the plan could not express, and the entity rule found four imports the survey missed. The plan above has been corrected to 6 and 16.

**The allowlist can only shrink in one direction.** Each entry names the slice that will delete it, and the fourth check fails the build when an entry no longer describes a real violation — so fixing a violation without deleting its entry is itself a failure. 52.8 deletes the three constants.

**Verification** — the guard was proved to bite, not merely to pass:

| Check | Result |
|-------|--------|
| `mvn clean verify` | **204 tests, 0 failures** (200 → 204: the guard went from 1 check to 5) |
| Counts printed by every run | `repository imports 17 (allowlisted 17)`, `entity imports 16 (allowlisted 16)`, `module import cycle 6 (allowlisted 6)` |
| Injected a cross-module repository import (`BillService` → `system.SysUserRepository`) | build **fails**, naming the file and the target |
| Injected a cross-module entity import (`BillService` → `prescription.Prescription`) | build **fails**, and the cycle check additionally reported three new cycles, including a four-module one (`billing↔prescription↔system↔patient`) |
| Injected an allowlist entry with no violation behind it | build **fails** with `these allowlist entries no longer describe a violation — delete them` |
| Both injections reverted | `git status` clean; the suite is green from the committed tree |

The injections were made in the working tree and reverted immediately; they are recorded here because a guard test's only value is the failure it produces.

### Slice 52.2 ✅ — `PatientLookup`, and the five reads that closed three cycles

`common/lookup/PatientLookup` answers two questions — `displayName(id)` and `allergies(id)` — and `module/patient/service/PatientFieldLookup` answers them from the patient module's own repository, through two new single-column reads (`PatientRepository.findNameById`, `findAllergiesById`). The five call sites the plan named now use it: `AppointmentService.toVO`, `BillService.toVO`, `PrescriptionService.toVO`, `ChatService.resolveName`, `CdsService.checkAllergyContraindications`. Each keeps the fallback it had (`""`, `"Unknown"`, or "no allergies recorded"), so nothing downstream can tell the difference.

**Scope-free and audit-free is the whole point of the interface, and it is now the interface's documented contract.** `PatientService.getById` is the obvious implementation and the wrong one: it calls `doctorPatientScope.requireAccess` (a doctor would start seeing 403 where a list used to render) and it carries `@Auditable(module = "patient", action = "VIEW", phiAccess = true)` (one PHI-read audit row per row of every list).

**`allergies` is encrypted too, which turned this from a rename into a measurement.** Both fields behind the lookup carry `@Convert(converter = AesAttributeConverter.class)` (`Patient.name`, `Patient.allergies`), so the single-column read only returns usable values if Hibernate applies the converter to a scalar JPQL select. Had it not, the appointment/bill/prescription lists would have rendered ciphertext — and `CdsService` would have compared ciphertext against allergy-class names, silently deleting every `DRUG_ALLERGY` warning. That is why the pre-change instance was probed **before** a line was written, and why the comparison below is a byte comparison rather than "the page still works".

**Order followed CLAUDE.md §8b**, since the running instance was the "before" half: probe the live pre-change instance → stop it → `mvn clean verify` → start the new build → probe again.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **204 tests, 0 failures**, `BUILD SUCCESS` |
| Counts printed by every run | `repository 12 (allowlisted 12)`, `entity 11 (allowlisted 11)`, `module import cycle 3 (allowlisted 3)` — down from **17 / 16 / 6** as planned |
| Cycles gone | `appointment ↔ patient`, `billing ↔ patient`, `patient ↔ prescription`. Left, each still tagged to its slice: `appointment ↔ system ↔ patient` (52.3+52.4), `patient ↔ prescription ↔ system` (52.3+52.7), `patient ↔ system` (52.4) |
| No wire change, both roles | 13 response bodies captured from the pre-change instance and from the new build are **byte-identical**: `/appointments`, `/prescriptions`, `/bills`, `/messages/conversations` plus `/patients`, as **admin** and as **doctor1**, and `POST /cds/check` for patient 100 (allergic to Penicillin) and 102 (no allergy). The names are plaintext in both runs — this is the measurement that the encrypted scalar read decrypts |
| CDS allergy path | identical three warnings, unchanged: `DRUG_DRUG/minor`, `DRUG_ALLERGY/contraindicated` (`Amoxicillin vs Penicillin`), `DRUG_ALLERGY/contraindicated` (cross-reactive); patient 102 still `passed: true, warnings: []` |
| Names in the browser | `/appointments` (5 rows), `/prescriptions` (3), `/billing` (4 bills + 2 charges) render `James Anderson` / `Maria Garcia` as **admin** and as **doctor1**; the chat sidebar renders `James Anderson`, `Maria Garcia` for doctor1 (admin has no conversations) — 0 console errors. Screenshots `/tmp/ui-shots/100-52.2-{admin,doctor}-{appointments,prescriptions,billing,chat}.png` |
| Query shape | the appointment list now issues `select p1_0.name from patient p1_0 where (p1_0.is_deleted = 0) and p1_0.id=?` **once per row** — the same one query per row as before, but one column where the repository call selected all 33 (`ssn`, `medical_history`, `allergies` included) and decrypted every one of them |
| Audit rows | audit total **296 before and 296 after** rendering 18 rows' worth of names (11 appointments, 4 bills, 3 prescriptions); the newest rows are `auth/LOGIN_SUCCESS` only — the lookup writes no PHI-read row |

**Not in this slice, deliberately:** the doctor name on the same rows (`AppointmentService:205`, `ChatService:114`, `PrescriptionService:191`) still comes from `SysUserRepository` — that is 52.3's `StaffLookup`, and the allowlist entries for it are still listed.

### Slice 52.3 ✅ — `StaffLookup`, and the accounts the lists were loading whole

`common/lookup/StaffLookup` answers `realName(id)` and `prescriberIdentity(id)` — the latter returning the `PrescriberIdentity(npi, deaNumber)` record a prescription is signed with — and `module/system/service/StaffFieldLookup` answers both from `SysUserRepository`'s two new narrow reads. The three call sites the plan named now use it: `AppointmentService.toVO`, `ChatService.resolveName` (the STAFF branch), `PrescriptionService` twice — the doctor name in `toVO` and the server-derived prescriber identity in `create`.

**`prescriberIdentity` is one query, not two, and the reason is worth stating.** The obvious implementation composes two single-column reads, but "the account row exists" and "the DEA column is non-null" are then indistinguishable, and an account with an NPI but no DEA would silently lose its NPI. A JPQL constructor expression (`SELECT new ...PrescriberIdentity(u.npi, u.deaNumber)`) keeps the row-exists semantics the old `findById(...).orElse(null)` had — at the cost of relying on the converter being applied to *constructor arguments*, a mechanism 52.2 had not measured. So it was measured, below.

**`deaNumber` is encrypted, and nothing exposes it for reading.** `PrescriptionVO` carries `prescriberNpi` but not the DEA, the NCPDP draft payload carries the pharmacy NPI only, and the stored column is ciphertext — so a defect here would be invisible to every existing surface: a prescription signed with `v1:...` instead of a DEA number. The check therefore reads the stored ciphertext of a row created **before** the change and one created **after**: both must be one encryption of a 9-character string. A value that had passed through the lookup as ciphertext would have been encrypted a second time and come out roughly twice as long.

Chat's STAFF branch needed a conversation to exist at all: the seed data has none (all eight seeded messages are PATIENT ↔ STAFF), so this slice's probe sends one staff-to-staff message first, in both phases, and reads it back as both parties.

**Verification**

| Check | Result |
|-------|--------|
| `mvn clean verify` | **204 tests, 0 failures**, `BUILD SUCCESS` |
| Counts printed by every run | `repository 9 (allowlisted 9)`, `entity 8 (allowlisted 8)`, `module import cycle 1 (allowlisted 1)` — from **12 / 11 / 3** |
| Cycles gone | `appointment ↔ system ↔ patient` and `patient ↔ prescription ↔ system` both closed, so the round's two three-module cycles are gone. Left: `patient ↔ system` (52.4) |
| No wire change, both roles | 14 response bodies captured from the pre-change instance and from the new build: `/appointments`, `/prescriptions`, `/bills`, `/messages/conversations` as **admin** and as **doctor1**, the doctor profile read, the created prescription, and the CDS allergy check. All are byte-identical apart from fields that must move — the new row's `id`, its item `id`, and `lastLoginTime` |
| Prescriber identity | the same create request is signed identically before and after: `prescriberNpi 1234567890`, `doctorName Dr. Sarah Mitchell`, `patientName James Anderson`; `/users/me` still reports `npi 1234567890`, `deaNumberLast4 ****4567` in both runs |
| **DEA decryption (the measured risk)** | H2 console, via the app's own datasource: `LENGTH(dea_number)` is **76 for the pre-change row 303, 76 for the post-change row 335, and 76 for `sys_user.dea_number` of the prescriber** — one encryption of the same 9-character value each, with distinct IVs (`01e…`, `014…`, `017…`). A ciphertext pass-through would have produced a ~111-character double encryption |
| SQL shape | since the restart: **26** `select su1_0.real_name from sys_user …` (one column per name resolved), **1** `select su1_0.npi, su1_0.dea_number from sys_user …` (the one create), and **4** whole-account reads — three `username=?` logins and one `id=?` profile read. The lists no longer load the account, whose 25 columns include the **password hash** |
| Names in the browser | `/appointments` (5 rows) and `/prescriptions` (5) render `Dr. Sarah Mitchell` in the Doctor column as **admin** and as **doctor1**; the chat sidebar shows `Dr. Sarah Mitchell` for admin and `Administrator`, `James Anderson`, `Maria Garcia` for doctor1 — the STAFF branch is visible, not just covered. 0 console errors. Screenshots `/tmp/ui-shots/103-52.3-{admin,doctor}-*.png` |
| CDS allergy path | unchanged (52.2 regression): the same three warnings for patient 100 in both runs |

**Found while measuring, not fixed here — landed as 52.3a, next section.** `LayeringGuardTest` matches `import` statements, so a cross-module reference written fully qualified is invisible to it. `AppointmentService` holds `com.example.medical.module.billing.repository.ChargeRepository` (line 37, used at 150/158 with `billing.entity.Charge` and `billing.entity.ChargeStatus`) with no import at all — a real appointment → billing repository edge that no count above includes, and one that `52.8`'s "0 imports, 0 cycles" would not catch. This slice removed the two FQN references it owned (`PrescriptionService`'s `system.entity.SysUser`); the guard gap itself is 52.3a.

### Slice 52.3a ✅ — the guard counts a reference however it is written

The three checks read `import` statements, so a reference written out in full was invisible to every one of them. `crossModuleReferences()` now collects both forms: the same `CrossModuleReference` record is built from `import` lines and from `com.example.medical.module.<module>.<package>.<Class>` occurrences in code, so the repository check, the entity check and the cycle graph all see one dependency rather than one syntax. Comments and javadoc are dropped before the second scan — a `{@link}` documents a type, it does not depend on it.

Two details in the pattern are load-bearing, and both are about not reporting a class that is not there: the class name must start with a capital (so `billing.entity.Charge` cannot split into `billing` + `entity.Charge`), and the package part excludes delimiters (so a match cannot run past `(` into the next call on the line).

**The rule's first run is the evidence.** With the allowlist untouched it failed twice, naming exactly the references the import scan could not see.

| Check | Result |
|-------|--------|
| Red state, allowlist untouched | `repository references 10 (allowlisted 9)`, `entity references 9 (allowlisted 8)`, cycles 1 — two assertion failures: `module/appointment/service/AppointmentService.java → billing.ChargeRepository` and `… → billing.Charge`. `ChargeStatus` is not reported: the enum exemption already worked on a fully-qualified reference, because it is decided by reading the target file |
| `mvn clean verify` | **204 tests, 0 failures**, `BUILD SUCCESS`; printed `repository references 10 (allowlisted 10)`, `entity references 9 (allowlisted 9)`, `module import cycles 1 (allowlisted 1)` |
| Documentation is not a dependency (A/B in one file) | `{@link com.example.medical.module.patient.entity.Patient}` added to `ChatService` — counts stay 10 / 9 / 1, build green. Adding instead the code form `com.example.medical.module.patient.entity.Patient probe = null;` to the same file makes it `entity references 10 (allowlisted 9)` and fails the build naming `module/chat/service/ChatService.java → patient.Patient`. Same file, same target, same target package — only the form differs |
| Injections reverted | `git checkout` on `ChatService`; the only modified file is the guard |

The two new allowlist entries named **52.3b**: the *write* behind them — charge generation on visit completion — cannot be inverted with a lookup interface, so it needs the billing module's own service, and that is a change with behaviour to preserve, not a metric to move.

### Slice 52.3b ✅ — the visit's charge is written by the module that owns charges

`AppointmentService.generateCharge` used to build a `Charge` itself through `billing`'s repository — a fully-qualified reference, which is why 52.3a was needed to see it at all. It now calls `ChargeService.createForVisit(appointmentId, patientId, doctorId, cptCodes, icd10Codes, visitType)`, and the amount rule (992xx → 90, else 100) and the DRAFT status moved with it. `AppointmentService` keeps the "one charge per visit" decision, expressed through the owning module's own read `ChargeService.visitAlreadyCharged(...)`.

**Why the guard check stayed in the caller rather than moving inside the audited method.** `@Auditable` advises the whole method, so a single `createForVisit` that checked and returned early would write a `charge/CREATE` row for a charge it never created — and this project's audit rows are immutable and hash-chained (`prev_hash` links every row to the last), so a false row is not noise, it is a defect in a compliance record. The read that answers "already charged" therefore sits next to the write, in the same module, and the write itself always creates.

**Test-first, and the red run earned its keep.** `AppointmentIntegrationTest.completingAVisit_shouldCreateTheChargeThroughTheBillingService` was written before the change and failed on exactly one assertion — `expected: <1> but was: <0>` for the `charge/CREATE` audit row — while its charge assertions (CPT, amount, DRAFT, and the chief-complaint-carried-as-`icd10Codes` mapping) passed against the old code. That failure is therefore also the "before" measurement for the behaviour this slice had to preserve: the charge itself was already right; only its audit trail was missing.

| Check | Result |
|-------|--------|
| Red state, before the change | 8 tests in the class, 1 failure: `completing a visit must leave a charge/CREATE audit row ==> expected: <1> but was: <0>`. The charge assertions passed, naming the gap precisely |
| `mvn clean verify` | **205 tests** (204 + the new one), **0 failures**, `BUILD SUCCESS`; `repository references 9 (allowlisted 9)`, `entity references 8 (allowlisted 8)`, `module import cycles 1 (allowlisted 1)` — the two 52.3b entries are gone, and the cycle count does not move: `appointment → billing` is a one-way service edge |
| Live probe, doctor1 completing a visit for patient 101 | `charge/CREATE` rows **0 → 1**; the charge carries `cptCodes 99213`, `amount 90`, `status DRAFT`, `visitType FOLLOW_UP`, `icd10Codes` = the visit's chief complaint; the audit row records `charge/CREATE`, user `doctor1`, patient `101`, and its detail masks every argument (`createForVisit(appointmentId=[PHI], …)`) |
| Live probe, the dedupe | a charge entered by hand for appointment 206 (`chargeAmount 55`) survives visit completion unchanged, **no second charge is created**, and the audit count moves by exactly 1 — the hand-entered charge's own row, so the visit path correctly wrote neither a charge nor a row |
| Injection-free | no temporary code in the working tree; the guard file is unchanged by this slice except for the two deleted entries |

`AppointmentService` now imports `ChargeService` rather than writing the type out in full: the form that hid this dependency is the form 52.3a made visible, and there is no reason to keep using it.

### Slice 52.4 ✅ — break-glass asks the patient module, and the last cycle closes

`EmergencyAccessController` was the last controller holding another module's repository (the M8.6 leftover) — a `PatientRepository` it used for one call, `existsById`. It now asks `PatientLookup.exists`, which `PatientFieldLookup` answers from a `COUNT` query. That was the last `system → patient` reference in the codebase, so the round's last cycle closes: **`patient ↔ system` is gone, and the module import graph is acyclic (0 cycles)**.

The two tests were written before the change and were **green against the old code**. That is the §8 case for a slice whose whole point is "no observable change": a test cannot be made red by an inversion that preserves behaviour, so what these do is pin the behaviour the inversion has to keep. They are worth having anyway, because the branch they cover — the `404` — had no test at all before this slice, and because writing them answered a question I could not settle by reading code: **does `existsById` see a soft-deleted patient?** Break-glass sits on the clinical path, so guessing was not an option; soft-deleting a patient through the API and asking for a token says the old form answers 404, i.e. the restriction already hid deleted rows, and a `COUNT` under `@SQLRestriction` preserves exactly that.

| Check | Result |
|-------|--------|
| Written first, green before the change | 4 tests in the class, 0 failures against the unmodified controller: `200` for a real patient, `404` for an unknown id, `404` for a soft-deleted patient (new), history intact (new) |
| `mvn clean verify` | **207 tests** (205 + the 2 new), **0 failures**, `BUILD SUCCESS`; `repository references 8 (allowlisted 8)`, `entity references 8 (allowlisted 8)`, **`module import cycles 0 (allowlisted 0)`** — `PENDING_CYCLE` is now `Map.of()`, and 52.8 deletes it |
| Live probe, doctor1 | `POST /emergency/access/100` → **200**, token, `patientId 100`, 30 minutes; `POST /emergency/access/999999` → **404** `Patient not found` |
| Live probe, the SQL | `select count(p1_0.id) from patient p1_0 where (p1_0.is_deleted = 0) and p1_0.id=?` — a count over the restriction predicate, selecting no PHI column, where the whole entity used to be a candidate |

### Slice 52.5 ✅ — the interface writes through the patient module

The two inbound interface paths no longer touch the patient module's stores. `PatientLookup` gains `idByMrn` (MRN is the key every interface message arrives with, and the column is unique), and the patient module gains the writes: `PatientRegistryService.createFromAdt` / `updateFromAdt` for an ADT message, and `ObservationIntakeService.ingest` for a lab message. The integration services keep the decisions that are about the *feed* — has this message been seen, does its MRN map to anybody — because those are interface questions, and because asking them through module reads is what keeps every audit row truthful (the same reasoning that kept 52.3b's "already charged" check in its caller).

**The audit rows became the patient module's, as the risk note predicted.** An ADT message now writes `patient/CREATE` (new MRN) or `patient/UPDATE` (known MRN, with the patient id attached), and a lab message writes `patient/LAB_INGEST` attributed to the patient whose observations it stored. The `integration/*` row each endpoint already wrote is unchanged and still written, so both the interface event and the PHI write it caused are on the trail.

The two rules that look like trivia but are not stayed where they belong: `normalizeAbnormalFlag` moved into the patient module (it exists because the column is `CHAR(1)` — a storage fact), and "not stated is not cleared" is now the documented contract of `apply`, so an ADT message that omits a field leaves the stored value alone.

**Verification**

| Check | Result |
|-------|--------|
| Red first, allowlist in place | 8 tests in `MirthIntegrationTest`, 2 failures — both audit assertions (`expected: <1> but was: <0>`): no `patient/CREATE` for the ADT-created patient, no `patient/LAB_INGEST` for the stored observations. The ack-shape and upsert tests were green before and after: they pin what must not move |
| `mvn clean verify` | **212 tests** (207 + 5), **0 failures**, `BUILD SUCCESS`; `repository references 5 (allowlisted 5)`, `entity references 5 (allowlisted 5)`, cycles 0 — the six 52.5 entries are gone |
| Live probe, ADT | `A08` for an unseen MRN → `{"status":"ACK","sourceMessageId":"fix-adt-a"}`, and the patient reads back with every field the message carried; the same MRN again with only a city → the city changes while `line1`, `state` and `zip` are left as they were |
| Live probe, lab | one result → `recordsCreated 1`; the same `sourceMessageId` again → `recordsCreated 0`; an unmapped MRN → HTTP 200 with `recordsCreated 0` |
| Live probe, audit | `patient/CREATE` for the ADT-created patient, `patient/UPDATE` with `patientId 104`, `patient/LAB_INGEST` with `patientId 100` — all by `admin`, all visible through `/audit-logs` |
| Test-side correction | the first version of the new ADT test sent `"stateCode"`, the Java field name rather than the wire key (`@JsonProperty("state")`); the live probe showed state never arriving, and the test now sends `state` and asserts it on create **and** on update |

**Found while writing this slice's tests — a real defect, not fixed here.** `observation` carries `UNIQUE KEY uk_source_message (source_message_id)`, and `LabResultService` builds one `Observation` per result item, all sharing that message id. So **any lab message with more than one result fails as a whole**: the second insert violates the index, `ConstraintViolationException` propagates, the transaction rolls back, and the endpoint answers 500 having stored nothing. A CBC panel is eight results, and both the ack contract (`recordsCreated`) and the seeded LOINC panel assume N > 1 — so the constraint is what is wrong. Moving it to `(source_message_id, loinc_code)` means a `schema.sql` change, a `DevSchemaGuard.SCHEMA_VERSION` bump, and an upgrade path for existing dev databases (`spring.sql.init` only creates tables that do not exist, so today's index survives on any database already built). That is a data-shape change with a migration story, not a module-boundary one, so it is planned below as **52.5a** rather than folded in here.

### Slice 52.5a ✅ — the observation key allows a panel

`observation` was keyed `UNIQUE (source_message_id)` while one row is written per result item, so a message carrying two results could store only one of them — and in fact stored none: the second insert violated the index, the transaction rolled back, and the endpoint answered 500. The key is now `(source_message_id, loinc_code)`, which is what the ingest actually means, and `DevSchemaGuard.SCHEMA_VERSION` went 1 → 2 in the same commit, which is the rule that file states.

**The migration story is the guard, and it was measured rather than described.** Starting the new build against the existing v1 database exits 1 without ever listening:

```
java.lang.IllegalStateException: Local database schema is v1 but this build expects v2 (jdbc:h2:file:.../medical_dev;...).
spring.sql.init never alters existing tables, so the new columns are missing. Delete the local database file
(default: ~/.medical-dev/data/, or the path in H2_DB_PATH) and restart to rebuild schema + seed data.
```

`README.md` already documents that reset and the bump rule, and `DevSchemaGuardTest` already covers the fresh-database and mismatch paths, so this slice needed no documentation beyond this record. The local database was rebuilt to run the probe below: the seed data is fresh, and the probe rows 52.4/52.5 had left are gone.

| Check | Result |
|-------|--------|
| Red first | the two-result assertion restored in `MirthIntegrationTest` → `Status expected:<200> but was:<500>`, with H2 naming the constraint: `Unique index or primary key violation: "PUBLIC.UK_SOURCE_MESSAGE_INDEX_8 ON PUBLIC.OBSERVATION(SOURCE_MESSAGE_ID ...)"` |
| `mvn clean verify` | **212 tests, 0 failures**, `BUILD SUCCESS`; this slice does not move the guard counts (5 / 5 / 0) |
| Live probe, the defect | a two-result message answers `recordsCreated 2`; an eight-result CBC panel answers `recordsCreated 8`, and `/patients/100/observations` reads the rows back with their LOINC codes, values and message ids |
| Live probe, idempotency | the same `sourceMessageId` resent answers `recordsCreated 0` — the message-level duplicate check never depended on the index |
| **Residual, measured and left alone** | one message that repeats a single LOINC code twice still answers **500**: the payload carries no specimen or sequence to tell the two results apart, so any tighter key would have to invent one. Silently keeping the first row would lose data, and a 400 would have to be defined per interface — a product decision, reported rather than guessed |

### Slice 52.6 ✅ — a clinical read model for the eCQM engine

`QualityMeasureService` walked `patientRepository.findAll()` and read the patient module's entities directly — the last read of its kind, and one that also cost an observation query per patient per LOINC. It now reads `common/lookup/ClinicalMeasureReader`: `patientPage(pageable)` for the patients a measure must consider, and `latestObservations(patientIds, loincs)` for the most recent value of each LOINC those patients carry. `PatientMeasureReader` answers both from the patient module's own repositories. Every predicate stayed where it was — only where the values come from changed.

**This is the slice the plan said would have to be designed.** The design decision was not the method names but what to pass through unchanged, because each of these would otherwise move the counters:

- **the condition text, not codes.** The measures match words in `Patient.medicalHistory`. The `problem` table holds codes, and they cover a different set of patients: patient 100 has Essential hypertension + Type 2 diabetes as rows, patient 103 has none, while the free text carries both. Reading codes would drop 103 out of CMS122 and CMS165.
- **age as a number, not a date of birth.** Age is `-1` when the date of birth is unknown, which every measure reads as out of range — the same rule, now applied inside the patient module, so DOB never leaves it.
- **the value as a string**, because the measures parse it and an unparseable value means "not on record"; and **the effective date**, because CMS125 needs it (a mammogram counts only within 27 months), not just the value.

Scope-free and audit-free, like the other lookups: the daily `QualityScheduler.calculateAllMeasures()` runs with no authenticated user, so anything the reader inherited from a login would break that path. The service now also pages (500 patients) instead of loading the whole table, and picks the newest value per patient and LOINC out of one ordered query — the same ordering the per-patient query used, so the row picked is the same one, including for rows that carry no effective date.

**Verification**

| Check | Result |
|-------|--------|
| Counters pinned first | `qualityReports_shouldMatchTheSeededCounters` was written against the old code and passed: CMS122 `2/0/2/2/100`, CMS125 `1/0/1/1/100`, CMS165 `2/0/2/1/50` — derived from the seed by hand (100 and 103 diabetic and hypertensive; latest HbA1c 7.8 and 8.5; latest BP 132/85 against 145/92; 103's mammogram inside 27 months) and then confirmed by running it |
| The same test after the rewrite | unchanged, still green — the three report payloads also compare **field for field identical** to the pre-change live responses, with only `calculatedAt` moving |
| Query shape, measured from the instance log | the same three `/calculate` calls: patient queries **3 → 3**, observation queries **7 → 3** (one batched query per measure instead of one per patient per LOINC: CMS122 2, CMS125 1, CMS165 4 before). Paging also bounds memory, where the old code loaded every patient |
| `mvn clean verify` | **213 tests** (212 + the new counter test), **0 failures**, `BUILD SUCCESS`; `repository references 3 (allowlisted 3)`, `entity references 3 (allowlisted 3)`, cycles 0 — only 52.7's entries remain |

**Found while making the suite green — a test-isolation defect, fixed here.** `PatientIntegrationTest`'s update and delete targeted **seeded patient 103**: the update's form carries `null` for every field it omits, so it wiped 103's date of birth and medical history, and the delete then soft-deleted the row — for every class that ran afterwards, since `cleanup-test-data.sql` restores 100–103 only by deleting rows whose name or MRN looks like test data. Nothing depended on patient 103 until these counters did; the first full run failed with `CMS122v11 denominator ==> expected: <2> but was: <1>`. Those two tests now act on the patient the class creates at order 36, which the cleanup script does remove.

### Slice 52.7 ✅ — the 360 bundle reads through the modules that own the visits and the medication

`PatientCaseService` built the portal's FHIR case bundle from `AppointmentRepository`, `PrescriptionRepository` and `PrescriptionItemRepository`, reading their entities field by field. It now calls `AppointmentService.listForPatient(patientId)` and `PrescriptionService.listForPatient(patientId)` — the same reads the staff views use, in the same order (`appointmentTime DESC`, `prescriptionDate DESC`), both plain scope-free reads — and maps the VOs. Decision 2 of this round's plan sanctions exactly that direction: the portal consumes other modules' services, and only their *stores* were off limits. `AppointmentStatus` is still imported, because it is an enum and the enum exemption is deliberate; the guard proves that by reading the target file.

**With this slice the round's metrics reach zero**: `repository references 0`, `entity references 0`, `module import cycles 0`, and all three allowlists are now empty maps. 52.8 deletes the constants themselves.

| Check | Result |
|-------|--------|
| Bundle pinned first | a characterization test written against the old code and green before the rewrite: the entry composition **and order** (Patient, Condition, AllergyIntolerance, the four seeded visits newest first, then the items of prescription 301 before 300), the bundle `total`, that a scheduled visit maps to `planned` and a completed one to `finished`, and that every medication request names its drug |
| Live bundle, field for field | the same request against the pre-change and post-change instances differs only in the two timestamps the code stamps itself — the bundle `timestamp`, and `dispenseRequest.validityPeriod.start` on each of the four medication requests (four lines of diff, both `new Date()` by construction). Entry order, ids and every other field are identical |
| `mvn clean verify` | **214 tests** (213 + the bundle test), **0 failures**, `BUILD SUCCESS`; `repository references 0 (allowlisted 0)`, `entity references 0 (allowlisted 0)`, `module import cycles 0 (allowlisted 0)` |

**A plan expectation corrected by measurement.** This row promised "the portal pages re-checked in the browser", but `GET /patients/{id}/case` has **no frontend consumer** — `medical-web/src` contains no reference to it, and nothing else in the backend calls it either. It is an API-only FHIR bundle for an external system, so the comparison above is against the response body rather than a screenshot. The portal pages that do consume these modules (the appointment and prescription lists) were not touched by this slice.

Also worth knowing when reading the test: the test database's bundle carries **11** entries against the dev database's **9**, because `cleanup-test-data.sql` restores appointments 202/203 to `scheduled` while the running dev instance's scheduler had already marked those past visits no-show. The test asserts the test database's state, and says so.

### Slice 52.8 ✅ — the allowlist deleted, and the rule written down

The three allowlist constants are gone, along with the fourth check that policed them (`allowlistsShouldNotContainFixedEntries`) and the `pending` parameter every assertion used to consult. What is left is the rule itself: `assertNoViolations` prints the count and asserts the set is empty, and the failure message says what to do instead — cross the boundary through the owning module's service, or through an interface in `common/lookup`.

**The guard was proved to bite without the allowlist**, because a guard that has only ever passed proves nothing (52.1's argument, applied to the state it was working towards). Two violations were injected into the working tree and reverted immediately:

| Injected | Result |
|----------|--------|
| `DashboardService` importing `patient.entity.Patient` | `entity references 1`, build **fails**: `a module must not reference entity references: [module/dashboard/service/DashboardService.java → patient.Patient] — cross the boundary through the owning module's service or an interface in common/lookup (see PatientLookup, StaffLookup, ClinicalMeasureReader)` |
| `ChatService` holding `com.example.medical.module.prescription.repository.PrescriptionRepository` **fully qualified**, with no import | `repository references 1`, build **fails** naming `module/chat/service/ChatService.java → prescription.PrescriptionRepository` — the form that was invisible before 52.3a |

The rule is now stated where a contributor will meet it: `CLAUDE.md`'s structure rules name the three sanctioned ways across a boundary (a `common/lookup` interface, the owning module's service, or consuming its DTOs and services as a caller) and say the guard has no allowlist; `docs/backend-architecture-explained.md` gains **§3.1 "Who may import whom"** with the table, the reasoning (a repository *is* the persistence contract — its audit, its scoping and its lifecycle live behind its service), and the enum exception. Its directory tree also gained the entries it had been missing (`common/annotation`, `common/job`, `common/lookup`, and the `integration` and `quality` modules) — the section was describing a tree two modules out of date.

| Check | Result |
|-------|--------|
| `mvn clean verify` | **213 tests** (214 − the deleted allowlist check), **0 failures**, `BUILD SUCCESS` |
| Counts printed by every run | `repository references 0`, `entity references 0`, `module import cycles 0` — no allowlist, nothing to consult |
| Both injections reverted | `git checkout` on the two files; the guard is green from the committed tree |

**Round 52 is complete.** The metrics it set out to move, from the plan's own table: repository references **17 → 0**, cross-module entity references **16 → 0** (status enums still shared, by design), import cycles **6 → 0**, and the allowlist mechanism that made the work incremental is deleted rather than left as a permanent dumping ground. Eight slices, each with its own measurement: 52.1's guard (which corrected the plan's numbers), 52.2's `PatientLookup`, 52.3's `StaffLookup`, 52.3a (the guard learns about fully-qualified references), 52.3b (the charge write that blindness had hidden), 52.4 (`PatientLookup.exists`, last cycle), 52.5 (`PatientRegistryService` / `ObservationIntakeService`, the interface writes), 52.5a (the constraint that made a lab panel fail), 52.6 (`ClinicalMeasureReader`), 52.7 (the 360 bundle through the owning services) and this one.

### Slices

Each slice is one commit, leaves `mvn clean verify` green, and carries its own evidence in the project's usual style (no-wire-change comparisons, live probes, role checks).

| # | Slice | Removes | Evidence to record |
|---|-------|---------|--------------------|
| 52.1 | Guard rules for cross-module repository/entity imports and import cycles, with the allowlist | — (introduces the metric) | the test prints the counts; `clean verify` green with the allowlist in place |
| 52.2 | ✅ `PatientLookup` for the patient's name and allergies — `AppointmentService:208`, `ChatService:119`, `PrescriptionService:194`, `CdsService:104`, `BillService:58` | `appointment↔patient`, `billing↔patient`, `patient↔prescription`; 5 repository **and 5 entity** imports (17→12, 16→11, 6→3) | the four lists compare **byte-identical** as admin and doctor, and the CDS allergy warnings are unchanged; query reduced to one column per row; 296 → 296 audit rows |
| 52.3 | ✅ `StaffLookup` for the clinician's name and prescriber identity — `AppointmentService:210`, `ChatService:116`, `PrescriptionService:103/196` | 3 repository **and 3 entity** imports (appointment/chat/prescription → system); both three-module cycles closed (12→9, 11→8, 3→1) | prescription `prescriberNpi`/`deaNumber` unchanged — DEA confirmed decrypted at 76-char ciphertext; chat sender names unchanged in API and UI |
| **52.3a** | ✅ **The guard counts a cross-module reference whether it arrives by `import` or fully qualified** — found while measuring 52.3, not in the original plan | — (adds the check; the references it finds became allowlist entries tagged 52.3b) | the rule's first run failed naming `AppointmentService` → `billing.ChargeRepository` and `billing.Charge`, neither of which any earlier count included; comments excluded by an A/B in one file; `clean verify` green at 10 / 9 / 1 |
| **52.3b** | ✅ **Appointment's charge generation goes through the billing module's service** — found by 52.3a, which is what made the violation visible | `AppointmentService` → `billing.ChargeRepository` / `billing.Charge`: the last cross-module **write** in this direction, and one an interface cannot invert (10→9, 9→8, cycles 3→1 unchanged at 1) | completing a visit still creates the same charge row (same CPT, amount and DRAFT status) — and now leaves the `charge/CREATE` audit row it used to skip; live probe 0 → 1 rows, and a hand-entered charge is still not duplicated |
| 52.4 | ✅ `EmergencyAccessController` → `PatientLookup.exists` | `patient↔system` — **the last cycle**; 1 repository reference (9→8, and 1→**0** cycles) | break-glass 200 for a real patient and 404 for an unknown id, both re-checked live; 404 for a soft-deleted patient pinned by test; the check is a `count` under `is_deleted = 0` |
| 52.5 | ✅ Integration writes through the patient module: the `AdtService` upsert and the `LabResultService` observation ingest | 3 repository **and 3 entity** references (integration→patient), including the only cross-module **write** path (8→5, 8→5) | acks unchanged (count present/absent per endpoint, duplicate and unmapped-MRN cases at 0); the writes now leave `patient/CREATE`, `patient/UPDATE` and `patient/LAB_INGEST` rows |
| **52.5a** | ✅ **The observation uniqueness constraint permits one row per result, not one per message** — found by 52.5's tests: a lab message with two results answered 500 and stored nothing | the defect, not a boundary: `uk_source_message` → `(source_message_id, loinc_code)`, `SCHEMA_VERSION` 1 → 2, migration via the guard (measured: the old database refuses to start and names the fix) | a two-result message answers `recordsCreated 2` and an eight-result panel `8`, both readable back; the duplicate-message case still answers 0; the repeated-LOINC case is measured and left as a product decision |
| 52.6 | ✅ A clinical read model for the eCQM engine (`ClinicalMeasureReader`: a patient page plus the latest observation per LOINC) | 2 repository **and 2 entity** references (quality→patient), the last of each (5→3, 5→3) | the three reports compare field for field identical and the counters are pinned by test; observation queries 7→3 for the same three `/calculate` calls; the read model deliberately passes the free-text conditions and the raw value through unchanged, because codes or parsing anywhere else would move the numbers |
| 52.7 | ✅ The portal's 360 aggregate reads through the owning modules' services (`PatientCaseService`; `PatientDataExport` only ever imported their DTOs) | 3 repository **and 3 entity** references, the last of both — **0 / 0 / 0**, allowlists empty | bundle entry composition and order pinned by test before the rewrite; the live bundle differs only in the two timestamps the code stamps itself; the endpoint has no frontend consumer, so there was no page to re-check |
| 52.8 | ✅ Allowlist emptied and deleted; the rule stated in `CLAUDE.md` and `backend-architecture-explained.md` (§3.1) | the allowlist mechanism itself — three constants, the check that policed them, and the `pending` parameter | **17 → 0 repository references, 16 → 0 entity references, 6 → 0 cycles**; two injected violations (an import and a fully-qualified reference) each fail the build by name, with the allowlist gone |

**Docs**: this section gains each slice's completion record; `CLAUDE.md`'s structure rules gain the repository/entity/cycle clauses; `docs/backend-architecture-explained.md` Layer 3/4 gains the dependency direction (it currently shows the directory tree but not who may import whom).

### Risks / trade-offs

- **Scoping and auditing change by accident.** If a lookup implementation calls `PatientService.getById`, it inherits doctor-scope enforcement and PHI-read auditing. Each slice verifies its endpoint as **admin and as doctor** — the failure mode is a doctor suddenly seeing 403 where the list used to render.
- **Extra queries.** `AppointmentService.toVO` resolves a patient and a doctor name per row. The implementations must stay single-column lookups; loading and decrypting a whole patient per row would turn one query into one-plus-decrypt-per-row. The list endpoints' query counts belong in the evidence.
- **Transaction shape.** A cross-module service call inside a `@Transactional` method joins the caller's transaction, which is fine, but a `@Cacheable` or `@Auditable` service method adds side effects the repository call never had. 52.5 is the delicate one: two write paths move into the patient module, so its audit rows and entity lifecycle become the patient module's — the ACK bodies and the integration tests are the check.
- **It is not free.** `common/lookup` adds an indirection over what is today a one-line repository call. It is worth it against four cycles and ten files that know another module's tables — but if a slice finds itself needing ten methods on an interface, that is the signal the *feature* belongs in the owning module instead. 52.6 is where that is most likely.
- **The counts are still a floor, not a proof.** Cycles are at 0 and every cross-module reference is visible to the guard (52.3a), but "0 repository references" only becomes true when the allowlist is empty — 8 entries remain, and 52.5–52.7 remove them. What the counts cannot show is a *write* that reaches another module's data by some path this scan does not read; none is known today.

**Not in this round:** the presentational status literals (the F4 leftovers listed in 51.13/51.14), enforcement of `sys_role.status` / `sys_menu.status`, and the dead formulary read surface — all still open and unaffected by this work.

## Round completion criteria

1. All 9 batches ✅ with their own `mvn test` / `tsc` / `npm run build` evidence recorded above.
2. `docs/API-LAYOUT.md`, `CLAUDE.md`, `README.md` refreshed for every behaviour change in this round (M1 Redis optionality + H2 reset, M2 export path, M7 size bound, M8 payloads).
3. No new runtime dependency; one removed (`hutool`).
4. Re-run of the review checklist: the F1–F14 items above closed or explicitly deferred with a reason.

---

## Round 53 — Backend-focused review follow-up (2026-10-15) — complete

**Status: all 11 findings fixed.** Every finding below carries a `Fix record` naming the red state observed before the change and the test that now holds it; the schema version, `docs/API-LAYOUT.md` and `README.md` moved with the slices that touched them. Nothing in this round is deferred — the two things it deliberately does not do are unchanged from the review: it does not deploy against Okta/Surescripts, and it does not add a commercial CDS subscription.

Reviewed baseline: [`4f629f4fb1584a266dc79e9e97a55828f9d41e00`](https://github.com/WangYufeng1990/medical-project/commit/4f629f4fb1584a266dc79e9e97a55828f9d41e00). Scope: backend authentication/authorization, appointment-to-billing flow, key rotation, audit integrity and FHIR serialization, with a smaller frontend review of appointment input and chat request handling. Source locations below refer to that baseline, not future line numbers.

This is a new follow-up to the earlier completed rounds. Their historical completion records remain intact; those records do not close the reproducible defects below. The project remains an H2 learning demo: R53-04 is specifically a defect in the production decoder and does not prevent the H2 profile from working. Recording its fix does not expand scope into a production deployment or introduce external infrastructure.

### Verification recorded during the review

- Existing backend suite: **213 tests, 0 failures, 0 errors**, `mvn clean test` passed on Java 17 with isolated in-memory H2. The first attempt hit the environment's Mockito dynamic-agent attachment restriction; rerunning with Byte Buddy loaded at JVM startup (`-DargLine=-javaagent:<byte-buddy-agent.jar>`) passed. The attachment failure is not a project defect.
- Separate temporary review probes reproduced R53-01, 02, 03, 05, 06, 07, 08 and 09 against isolated H2 / the actual application code. R53-04 was reproduced by signing and decoding a token with the actual production configuration classes, without an external Okta service.
- Frontend: `npm run check` passed with **0 ESLint errors, 4 warnings** and a passing TypeScript check; `npm run build` passed. R53-10 was checked with a fixed America/Los_Angeles timestamp. R53-11 is a static asynchronous-flow finding, not a browser reproduction.
- The review probes used synthetic data and did not modify application source. They were temporary review tooling, **not committed regression tests** — so every fix below began by writing the test it needed and recording the state it failed in. That red record, not the probe, is what each `Fix record` cites.
- This was not a full production/Okta/Redis/MySQL integration run. Passing the existing suite does not cover the missing scenarios listed below.

### Finding index

P1 means fix first because the defect breaks a security boundary, risks unreadable data, or blocks an entire authentication path. P2 means a concrete workflow/data/rendering defect to fix next.

| ID | Priority | Finding | Status |
|----|----------|---------|--------|
| R53-01 | P1 | Patient access and refresh tokens survive password changes and account disablement | [x] Done |
| R53-02 | P1 | Appointment conflict lookup discloses out-of-scope patient information | [x] Done |
| R53-03 | P1 | Resumed key rotation stops at an already-migrated batch and reports no remaining work | [x] Done |
| R53-04 | P1 | Production local JWT validator requires three mutually exclusive issuers simultaneously | [x] Done |
| R53-05 | P1 | Failed patient-login counters roll back with the authentication exception | [x] Done |
| R53-06 | P2 | An appointment that has started cannot be marked completed without changing its time | [x] Done |
| R53-07 | P2 | Visit-generated charges store chief complaint in the ICD-10 field | [x] Done |
| R53-08 | P2 | Database timestamp precision changes invalidate audit row hashes | [x] Done |
| R53-09 | P2 | FHIR Observation serialization fails when effective time has nonzero seconds | [x] Done |
| R53-10 | P2 | Appointment datetime-local minimum is generated in UTC instead of local time | [x] Done |
| R53-11 | P2 | Late chat responses can replace the currently selected conversation's messages | [x] Done |

### R53-01 — Revoke patient sessions on credential/state changes

**Location:** `module/patient/controller/PatientAuthController.java:155–188` (refresh), `security/JwtClaimMapper.java:45–56` (patient accounts excluded from staff revocation), and `module/patient/service/PatientAccountService.java` (password change). Backend paths are relative to `medical-server/src/main/java/com/example/medical/`.

**Observed:** after a successful patient password change, the original access token still reads `GET /api/v1/patient/me` with HTTP 200, and the original refresh token still obtains new tokens with HTTP 200. After setting the synthetic account to disabled, both old-token paths still return 200. Refresh verifies signature/claims but never loads the current patient account or checks its status/password-change state.

**Suggested solution:**
- Add a patient-specific account/session revocation contract through `common/security`, implemented by the patient module. Keep staff IDs and patient IDs as separate identity domains; do not reuse `sys_user` revocation by numeric ID.
- Validate patient/account existence, enabled status and session validity on both access-token authentication and refresh. A credential/session version claim checked against the database is preferable to fragile same-second `iat` comparisons; if using timestamps, specify precision and boundary semantics explicitly.
- Invalidate existing sessions on password change/reset and account disablement. Check the current account again before issuing refresh replacements. Preserve the rule that refresh tokens cannot authenticate normal API calls.
- Make the patient profile UI handle the chosen post-password-change behavior explicitly (e.g. clear patient credentials and return to login if all sessions are invalidated). Do not silently invalidate credentials while leaving the page looking authenticated.

**Tests first:** add `PatientSessionRevocationIntegrationTest`: valid credentials can access and refresh; after password change, both original tokens are rejected; a disabled/deleted patient cannot refresh or access; a new login with the new password works. Include staff/patient numeric-ID overlap and same-second changes in the identity/version tests. Cover password reset through the same revocation contract without exposing reset tokens in production responses.

**Fix record — done.** The credential marker, not the clock: a patient token now carries the moment its account's password last changed (`pwd`, the same marker for a never-changed password), and `PatientSessionCheck` — the patient-side counterpart of `AccountRevocationCheck`, in its own identity domain — answers whether the account still exists, is enabled, and still carries that marker. `JwtClaimMapper` asks it on every request (where it previously skipped patient tokens entirely, because a patient id must never be compared with `sys_user` revocation), and `/patient/login` and `/patient/refresh` mint with the current marker. Refresh re-reads the account first, so an old refresh token cannot resurrect a session it was issued for.

Comparing markers is an **equality test, not a timestamp comparison**, which is what the finding asked for and what keeps a password change in the same second as a login from invalidating the session that login just created. No schema change was needed: `patient_auth.password_changed_at` already existed and both password paths (change and reset) already set it.

| Check | Result |
|-------|--------|
| Red, before the fix | `PatientSessionRevocationIntegrationTest`: 7 tests, **4 failures** — the old access token answered 200 after a password change, the old refresh token still minted replacements (200), and a disabled **and** a removed account kept answering 200. The three that passed are the anti-over-correction guards: a session that nothing changed keeps working, the new password signs in, another patient's password change is none of this session's business |
| Green, after the fix | the same 7 pass, and the live instance agrees: after a real password change over HTTP, the old access token answers **401**, the old refresh token answers **401**, and the new password signs in with **200** |
| Browser, `/patient/profile` | changing the password shows *"Password changed. Please sign in again with your new password."*, lands on `/patient/login`, and clears `patientToken`, `patientRefreshToken` and `patientInfo` — the page no longer looks authenticated while holding tokens the server will refuse. Screenshot `/tmp/ui-shots/105-r53-01-after-password-change.png` |
| Existing test corrected | `PatientPortalIntegrationTest.patientChangePassword_shouldApplyAndBlockReuse` assumed a successful change left the session usable and kept calling the endpoint with the dead token. It now runs on patient 3 (which nothing else signs in as), re-signs in after each change, and asserts the pre-change token is refused — the reuse-policy intent is unchanged |
| `mvn clean verify` | **229 tests** (222 + 7), **0 failures**, `BUILD SUCCESS`; `npm run check` clean |

Two things left deliberately outside this slice, both worth their own decision: the access token's 24-hour and refresh token's 30-day lifetimes are unchanged (tighter TTLs are a product call, not a defect), and a token minted *before* this fix carries no marker and is therefore refused — correct for a session contract, but it does sign every live portal session out once on deploy.

**Review follow-up (same slice, before it was closed).** A review of the first commit found three gaps. All three were reproduced here before being fixed, and the first two are the reason the slice is only marked done now.

1. **A deleted patient's session survived (P1).** The check consulted `patient_auth` only, and `PatientService.delete` soft-deletes the *patient* while leaving the credential row intact — so an old access token kept working and its refresh token kept minting, against portal endpoints that are keyed by patient id rather than by the patient's own visibility. The regression test also cheated: it deleted the credential row by hand instead of using the deletion path. Both are fixed: `PatientAuthSessionCheck` now also requires the patient record (`countById`, which runs under the entity's `is_deleted = 0` restriction), refresh asks the same question instead of re-deriving its own, and `afterThePatientIsDeletedItsSessions_shouldBeRejected` logs in, calls `DELETE /api/v1/patients/{id}` as an admin, and requires **401** on both the old access token and the old refresh token.
2. **Ending the session did not clear the client's cache (P1).** The password-change path cleared the token store and navigated inside the SPA, leaving React Query's in-memory entries — keyed `['me', 'profile']`, `['me', 'appointments']` and so on, with **no patient identity in the key** — available to whoever signed in next on that browser, and the dashboard's 60-second `staleTime` renders them without refetching. `endPatientSession(queryClient)` now cancels and clears the cache, drops the credentials, and leaves the SPA; the same hole existed on the ordinary logout paths, so both use it. Measured in the browser: after a password change the page lands on `/patient/login`, the three storage keys are empty, and a marker planted on `window` beforehand is **gone** — a full page load, where a SPA navigation would have kept it.
3. **The probe account was left modified, making the suite order-dependent (P2).** `PatientPortalIntegrationTest` rewrote patient 3's password and never restored it, and `PatientSessionRevocationIntegrationTest` snapshotted whatever state it found — so forcing Surefire to run Portal first made the session class fail (`401` where it expected `200`), which is how the green "229 tests" of the first commit was partly luck of filesystem ordering. Fixed at the source: `IntegrationTestSupport.restorePatientPortalAccount` puts a seeded portal account back (seeded password, no credential marker, enabled, unlocked, history cleared, patient row un-deleted) and rebuilds the row when a test removed it; the session class restores from that instead of a snapshot, and the portal class restores patient 3 in `@AfterAll`. Verified by re-running the order that exposed it: `-Dsurefire.runOrder=alphabetical` with both classes green (15 + 8).

`mvn clean verify` after the rework: **230 tests** (one more than before — the real-deletion regression), **0 failures**; `npm run check` clean.

### R53-02 — Separate conflict detection from disclosure of patient details

**Location:** `module/appointment/controller/AppointmentController.java:35–42`, `module/appointment/service/AppointmentService.java:161–169`, and `AppointmentVO`.

**Observed:** a doctor receives 403 for direct access to a synthetic patient outside their scope, but `GET /api/v1/appointments/conflicts?doctorId=...&time=...` returns 200 and that patient's chief complaint. The endpoint accepts another doctor's ID and returns complete `AppointmentVO` records, including patient name, clinical text and notes, without patient-scope enforcement.

**Suggested solution:**
- Preserve the full internal occupancy/conflict check so bookings cannot bypass conflicts just because the conflicting patient is outside the caller's scope.
- Return a dedicated minimal availability/conflict DTO from the public endpoint (occupied time/duration or a conflict flag), with no patient identity or clinical fields. If detailed conflicts are needed, require explicit patient authorization before including them.
- Update `medical-web/src/api/appointment.ts`, its types and the conflict display in `views/appointments/index.tsx` to consume the new response. Remove the current patient-name display for unrestricted availability checks.
- Document the reduced response in `docs/API-LAYOUT.md`.

**Tests first:** extend `AppointmentIntegrationTest`: an out-of-scope doctor cannot retrieve patient identifiers/clinical text through conflicts, but the same occupied slot still blocks booking; authorized appointment detail reads remain scoped and functional.

**Fix record — done.** The endpoint answers occupancy and nothing else: `ConflictSlotVO` carries `appointmentTime` and `durationMinutes`, where it used to hand back whole `AppointmentVO` rows (eighteen fields, including `patientName`, `chiefComplaint` and `notes`) for any doctor's calendar. The two callers are now separate in the service, so the split is structural rather than a filter someone could widen later: the public path maps to the slot DTO, and booking still asks `hasConflict`, which must see every appointment of that doctor no matter whose patient occupies it.

| Check | Result |
|-------|--------|
| Red, before the fix | `conflictLookup_shouldDiscloseNoPatientDetails` asserted the response key set and got eighteen fields: `[appointmentTime, checkInTime, checkOutTime, chiefComplaint, cptCode, createTime, department, description, doctorId, doctorName, duration, icd10Codes, id, notes, patientId, patientName, status, visitType]` |
| Green, after the fix | the same test passes, and the live endpoint returns `[{"appointmentTime":"2026-05-20T10:30:00","durationMinutes":30}]` |
| The guard against over-correcting | `anOccupantOutsideTheCallersScope_shouldStillBlockTheBooking`: an appointment for patient 103 with the same doctor still refuses doctor1's booking with **409**, so narrowing the response did not narrow the check. It deletes its occupant afterwards — leaving it would quietly widen doctor1's patient scope (which is derived from exactly these rows) for every later test |
| Detail reads unchanged | `detailReadOutsideTheCallersScope_shouldBeForbidden`: the same record is still **403** through `GET /appointments/{id}`, so "who is in this slot" remains answerable only through the scoped path. The appointment belongs to doctor 3, a doctor the caller has no relationship with, so the test does not lean on any seeded patient's scope |
| Browser, `/appointments` → Add | the conflict warning now reads `Doctor already has 1 appointment(s) within 30 minutes: 2026-05-20T10:30:00 (30m)` where it used to append the patient's name. Screenshot `/tmp/ui-shots/107-r53-02-conflict-warning.png` |
| `docs/API-LAYOUT.md` | the `/conflicts` row states the new response shape and why it changed |
| `mvn clean verify` / `npm run check` | see the combined verification below (this slice's own class: 11 tests green) |

### R53-03 — Finish the scan before declaring key rotation complete

**Location:** `common/job/KeyRotationService.java:169–208`, especially `return count` and `if (batchCount == null || batchCount == 0) break`.

**Observed:** with the first 500 non-null rows already encrypted by the new key and row 501 still using the old key, calling the actual column migration leaves row 501 untouched while `remainingByTable` reports 0. This is the shape of an interrupted rotation restarted from offset zero. The outer loop subsequently marks rotation complete; retiring the old key or rotating again can make skipped rows unreadable.

**Suggested solution:**
- Track rows scanned separately from rows rewritten. Stop only at the actual end of the scan, never merely because a batch required no changes.
- Prefer keyset paging by ID to an OFFSET over a changing non-null result set. Make restart/resume idempotent across mixed-key batches.
- Use a compare-and-set update against the ciphertext read, or an equivalent locking/version strategy, so re-encryption does not overwrite a concurrent clinical edit.
- Verify that no old-key rows remain before publishing completion/allowing key retirement. Keep failed or unreadable rows visible as unresolved work instead of converting their count to zero. Interruption must not report completion.
- Retain the required previous key until migration is verified complete.

**Tests first:** add `KeyRotationServiceIntegrationTest` with 501 rows: first 500 new-key / final row old-key; resume after a partial batch; ordinary all-old data; and a concurrent edit that must survive migration. Assert both ciphertext readability with the new key and truthful completion/remaining status.

**Fix record — done.** The column walk is now keyset paging (`WHERE id > ? ORDER BY id LIMIT n`) that ends when a batch comes back **empty** — never because a batch happened to need no rewrite, which is how a resumed rotation stopped at its first already-migrated page. Two further rules make the report trustworthy: every rewrite is a compare-and-set (`... WHERE id = ? AND column = ?`) so a clinical edit that lands mid-rotation wins instead of being overwritten, and the number reported is a second read-only pass that counts the rows still on the previous key — a scan can no longer claim zero while leaving ciphertext only the retired key can read. Completion (and `markRotationComplete`, which retires the old key) is published only when that count is zero across every column; otherwise an `ROTATION_INCOMPLETE` audit row is written, the old key stays in service, and the nightly safety check resumes.

**Scale note on the test.** The unit of the bug is a batch boundary, not the number 500, so the tests drive one column with an explicit batch size (`migrateColumn(tc, 1)`) instead of inserting 501 rows; the same scenario is a batch of already-migrated rows followed by one that is not. The pacing sleep between batches is a field the test sets to zero, which took the class from 38 s to 8 s.

| Check | Result |
|-------|--------|
| Red, before the fix | `aBatchThatNeedsNoRewrite_shouldNotEndTheScan`: the legacy row behind an already-migrated batch was **never migrated** (`still on the previous key: expected false, was true`) while the column still reported zero remaining. `aRowEditedSinceItWasRead_shouldNotBeOverwritten`: the rotation overwrote the concurrent edit (`expected 0, was 1`) |
| Green, after the fix | both pass, plus `countPreviousKeyRows_shouldReportWhatIsStillOnTheOldKey` (the reporting primitive) and `aFullRun_shouldMigrateAndComplete` (a whole run migrates what it finds and only then retires the key) |
| `mvn clean verify` | **239 tests** (235 + 4), **0 failures**, `BUILD SUCCESS` |
| `docs/API-LAYOUT.md` | `/rotation-status` now states what `remainingByTable` means, and what it used to mean |

Left alone deliberately: the review also suggested keyset-plus-CAS *plus* a row-level resume checkpoint. Resuming from a checkpoint solves a cost problem (re-scanning already-clean pages), not a correctness one — the verification pass already decides completion — so it stays out of a defect fix. Also unchanged: a rotation runs *columns* sequentially and an exception still aborts the whole run (now without publishing completion), which is the pre-existing behaviour.

### R53-04 — Accept one trusted issuer, not every issuer at once

**Location:** `common/config/SecurityConfigProd.java:65–72`; also inspect `CompositeJwtDecoder` and `security/DevJwtEncoder` for issuer routing consistency.

**Observed:** a patient token signed by the production configuration's encoder is rejected by its local decoder with `JwtValidationException: The iss claim is not valid`. `DelegatingOAuth2TokenValidator` combines validation failures; three distinct issuer-equality validators mean no single token can pass them all.

**Suggested solution:**
- Use one validator that accepts membership in the explicitly trusted local-issuer set, or select an issuer-specific validator/decoder after routing.
- Retain signature and expiration/not-before validation; do not fix this by dropping issuer validation.
- Align the emitted issuer values with the trusted set. `DevJwtEncoder` currently derives issuer from roles and ignores a caller-provided `iss`, including the refresh builder's special issuer; choose and document a consistent contract.
- Keep local patient/emergency trust separate from external IdP validation.

**Tests first:** add `SecurityConfigProdTest` exercising the actual encoder/decoder with a synthetic key: valid patient, refresh and emergency tokens validate according to the chosen issuer contract; wrong issuer, wrong signing key and expired tokens fail. Retain a request-level check that a refresh token is rejected as an access token.

**Fix record — done.** `TrustedIssuerValidator` (`common/config`) accepts membership in a trusted set, and `SecurityConfigProd.localDecoder()` now composes it with `JwtTimestampValidator` instead of three ANDed issuer validators. The trusted set is no longer a fourth copy of the issuer strings: it is built from `DevJwtEncoder.STAFF_ISSUER_URI` and `PATIENT_ISSUER_URI`, the two values the encoder can actually emit, so "the decoder trusts what the signer signs" is structural rather than remembered. The third value (`.../patient/refresh`) is gone, and with it the refresh builder's `.issuer(...)` line that the encoder silently overwrote — a token type is distinguished by its `scp`/`roles` claims, as it always was in practice.

Measured red→green (the four positive cases were red, the three negative ones green before *and* after, which is what makes them useful):

| Check | Result |
|-------|--------|
| Red, before the fix | `SecurityConfigProdTest`: 7 tests, **4 errors** — staff, patient, refresh and emergency tokens all failed with `An error occurred while attempting to decode the Jwt: The iss claim is not valid`, i.e. the decoder refused every token this server signs |
| Green, after the fix | 7 tests, 0 failures. The negative cases assert *which* check refuses them, so none of them can pass for the wrong reason: the foreign issuer names `iss`, the other key names `signature`, the stale one names `expired` |
| `mvn clean verify` | **220 tests** (213 + 7), **0 failures**, `BUILD SUCCESS` |
| Encoder contract | the test decodes each token the encoder emits for `ADMIN`/`DOCTOR`/`PATIENT` roles, so a future divergence between signer and decoder fails here rather than in production |

Scope note, since this finding is about the `prod` profile only: the same absence of coverage is the real reason it survived — no test built the production security configuration at all, and the dev/h2 profiles use a different decoder, so the demo was never affected. The request-level rule that a refresh token cannot act as an access token is unchanged and still covered by `AuthIntegrationTest`; a `SecurityConfigProdTest` case for it would need a MockMvc context on the prod profile, which is a separate decision.

### R53-05 — Commit failed patient-login attempts independently

**Location:** `module/patient/controller/PatientAuthController.java:65–90`, its `recordFailedAttempt`, and `module/patient/repository/PatientAuthRepository.java`. The staff-side `module/system/service/LockoutService` already demonstrates the separate-transaction pattern.

**Observed:** six consecutive wrong passwords all return 401; the persisted `failed_attempts` stays **0**; a correct password immediately afterward returns 200. The repository increment joins the controller's transaction, which rolls back when `BusinessException` escapes.

**Suggested solution:**
- Move the atomic failure increment/lock update into a patient-module Spring service using `REQUIRES_NEW`. Invoke it through the injected proxy, not by self-invocation.
- Do not make the entire login transaction commit arbitrary partial changes on authentication exceptions.
- On success, ensure counter reset and last-login changes do not merge a stale detached `PatientAuth` over the reset performed by a bulk update.
- Preserve generic invalid-credentials responses and the existing lock duration.

**Tests first:** add a real transactional integration test in `AuthIntegrationTest`: five wrong passwords persist the threshold; the next attempt, even with the correct password, is locked; after expiry, a successful login leaves counters reset. Restore the synthetic account between cases. Mockito verification that an increment method was called does not prove the counter committed.

**Fix record — done.** `PatientLockoutService` (patient module) owns both writes, each in `Propagation.REQUIRES_NEW`, mirroring the staff-side `LockoutService`: `recordFailedAttempt` increments and arms the 15-minute lock, `resetFailedAttempts` clears the counter **and** stamps the login in the same bulk update. The controller no longer touches the counter or saves `PatientAuth` at all.

That last part fixes the second half of the finding, which the headline reproduction hides: the old success path reset through a bulk update and then called `save(auth)` on the entity it had read *before* that update, so the entity's stale `failedAttempts` was merged straight back over the reset. With the counter never persisting, nobody could see it.

| Check | Result |
|-------|--------|
| Red, before the fix | `AuthIntegrationTest.patientLockout_shouldCountFailuresAndResetOnSuccess`: `five failed attempts must be persisted, not rolled back with the 401 ==> expected: <5> but was: <0>` — five 401s left the counter at zero, so no lock ever engaged and the correct password worked on the sixth attempt |
| Green, after the fix | the same test passes end to end: five failures persist and lock the account; the correct password is refused with 403 while the lock holds and does not add another failure; once the lock is backdated, a real login succeeds and leaves `failed_attempts = 0`, `locked_until = null` |
| **The stale-merge half, proved by injection** | `patientLogin_shouldClearAPreExistingFailureCount` plants four failures, logs in successfully and requires the counter to end at 0. Re-injecting the pre-fix sequence (bulk reset followed by `save` of the stale entity) turns it red: `expected: <0> but was: <4>`. Without that test the merge would have gone on hiding behind a counter that never rose above zero |
| Weak mock test rewired | `PatientAuthControllerTest` verified the controller's calls on the *repository*; it now verifies them on `PatientLockoutService`, and its `save(auth)` expectation is gone with the stale write. As the finding notes, this proves wiring, not commit — the integration test above is what proves commit |
| `mvn clean verify` | **222 tests** (220 + 2), **0 failures**, `BUILD SUCCESS` |

The probe account is `patient2`: no other test signs in as it, and the test restores its row in a `finally` block, so a locked account cannot leak into another class the way the patient-module seed data leaked in 52.6.

### R53-06 — Allow clinical status updates after the appointment start

**Location:** `module/appointment/service/AppointmentService.java:116–137`; related UI minimum-time handling is R53-10.

**Observed:** updating an appointment scheduled ten minutes earlier to status COMPLETED returns 400, `Cannot schedule appointments in the past`, even when the appointment time is unchanged. This blocks the normal completion-to-charge workflow.

**Suggested solution:**
- Apply the future-time rule when creating an appointment or actually rescheduling it. An unchanged historical start time must not prevent allowed status/clinical-field changes.
- Keep terminal-state protections and explicitly validate permitted transitions. Do not work around the validation by rewriting the recorded appointment time.
- Ensure completion generates one charge through the existing billing service and retains conflict checks when the schedule changes.

**Tests first:** extend `AppointmentIntegrationTest`: a past-start SCHEDULED/ARRIVED appointment completes without changing its time and generates one charge; rescheduling into the past remains rejected; an already terminal visit remains protected. Use this scenario with R53-07's field assertions.

**Fix record (R53-06) — done.** The future-time rule now belongs to *scheduling*: `update` asks whether the request changes the time or the doctor, and only then requires the result to be in the future. A status or clinical edit leaves the recorded start time alone, so a visit that has already started can be recorded; an actual reschedule into the past is still refused. The doctor lock and the conflict check are skipped when nothing about the schedule changed — they were being taken for a status flip.

| Check | Result |
|-------|--------|
| Red, before the fix | `completingAPastVisit_shouldSucceedWithoutChangingItsTime`: the visit is created in the future, its `appointment_time` is moved ten minutes into the past (the state a real visit is in), and the completion answers `400 Cannot schedule appointments in the past` |
| Green, after the fix | the completion answers 200, the charge it generates is found, and the recorded start time is untouched |
| Guard | `reschedulingIntoThePast_shouldStillBeRejected` keeps the rule where it belongs: moving a future booking into the past is still 400 |

Both tests remove what they created — including, for the completed visit, the charge and the appointment rows, because a completed appointment cannot be deleted through the API and leaving it would change the counts other classes assert.

### R53-07 — Preserve diagnosis codes in visit-generated charges

**Location:** `module/appointment/service/AppointmentService.java:141–144` and `module/billing/service/ChargeService.createForVisit`.

**Observed:** an appointment with `icd10Codes = R51.9` and chief complaint `synthetic headache` creates a charge whose `icd10_codes` is `synthetic headache`. The caller passes `a.getChiefComplaint()` in the parameter named `icd10Codes`; charge-to-bill conversion propagates the wrong value.

**Suggested solution:**
- Pass `a.getIcd10Codes()` to the existing diagnosis-code parameter. If the charge also needs clinical complaint text, model that as a separate field with its own storage/privacy rules.
- Preserve the charge amount, CPT, appointment link, DRAFT status and deduplication behavior.
- **Update the existing characterization assertion:** Slice 52.3b explicitly preserved the chief-complaint-as-ICD mapping and described it as unchanged behavior. That historical comparison is not evidence that the mapping is correct; the new regression test must assert distinct complaint/code values and expect the actual diagnosis code.
- Inspect previously generated demo charges/bills if correcting historical records; do not blindly reinterpret arbitrary text as diagnosis codes.

**Tests first:** extend `AppointmentIntegrationTest.completingAVisit_shouldCreateTheChargeThroughTheBillingService` and billing conversion coverage: complaint and ICD-10 deliberately differ; both charge and resulting bill retain `R51.9`; no duplicate charge or extra false CREATE audit is emitted.

**Fix record (R53-07) — done.** `generateCharge` now passes `a.getIcd10Codes()` into the parameter it always meant, so a visit's charge carries the diagnosis code rather than the chief complaint. **The characterization assertion from 52.3b was updated deliberately**: that slice pinned "the chief complaint is carried onto the charge as `icd10Codes`" as behaviour to preserve, which was a faithful record of what the code did and no evidence that it was right — the review said so explicitly, and the test now asserts the opposite, with the complaint and the code set to deliberately different values (`synthetic headache` against `R51.9`).

| Check | Result |
|-------|--------|
| Red, before the fix | `completingAVisit_shouldCreateTheChargeThroughTheBillingService`: `expected: <R51.9> but was: <synthetic headache>` — the complaint, verbatim, in the diagnosis field |
| Green, after the fix | the charge reads `R51.9`, and so does anything converted from it (charge → bill copies `icd10Codes`) |
| Historical data | not backfilled. Existing demo charges whose `icd10_codes` hold complaint text stay as they are: reinterpreting free text as diagnosis codes would be a guess, and the dev database is regenerated from seed on a schema change anyway |

### R53-08 — Hash the same timestamp that the database stores

**Location:** `common/audit/AuditLog.java:57–75`, `AuditLogWriter`, `AuditLogService.verifyIntegrity`, and the `audit_log.create_time` column definition.

**Observed:** an audit record with `createTime = 2026-10-15T12:34:56.123456789` returns from H2 as `2026-10-15T12:34:56.123457`. Recomputing its hash after a save/read round trip does not match the persisted hash, without any tampering.

**Suggested solution:**
- Define a canonical timestamp precision and serialization for hashing, normalize before calculating the hash, and persist that exact value. Ensure the selected precision matches the supported schema rather than assuming Java nanoseconds survive storage.
- If schema precision changes, update the schema version and document the supported database upgrade/reset path.
- Treat existing hash-format records explicitly. Do not silently rewrite immutable historical hashes to make verification green; define a versioned/legacy verification policy or document an intentional demo-data reset.
- Verify the persisted record through a fresh persistence context, not the same in-memory entity that computed its hash.

**Tests first:** add `AuditIntegrityIntegrationTest`: nanosecond input survives normalization plus database round trip with a valid hash; a multi-row chain validates after fresh reads; changing a hashed field is detected. Assert the timestamp normalization contract directly.

**Fix record (R53-08) — done.** Measuring the live database first changed what this finding is. `GET /api/v1/audit-logs/verify` on the dev instance answered `{"intact":false,"brokenRowId":4}` over 43 rows, and the SQL under it named the cause: rows 3 and 4 both carried `prev_hash = 6d3b…` (row 2's digest), and rows 6 and 7 both carried row 5's. That is not a timestamp, it is a **forked chain** — `AuditLogWriter.writeAsync` ran "read the head, insert the successor" unserialised on an `auditExecutor` whose core size is 2, so two audit threads read the same head and chained from it. The timestamp defect the review described is real but latent: `AuditLog.onCreate` hashed `LocalDateTime.toString()` at full precision while `audit_log.create_time` stores what the column allows.

| Check | Result |
|-------|--------|
| Red, before the fix — precision | `microsecondPrecisionSurvivesTheRoundTrip`: written at `2026-01-01T00:00:00.123456789Z`, the stored digest `0c53fd27…` does not equal the recomputed `66e00fe4…` |
| Red, before the fix — chain | `concurrentAsyncWritesChainOntoDistinctPredecessors`: 16 async appends land as 16 rows with **15** distinct predecessors — one pair chained from the same row, the dev-database symptom reproduced in a test |
| Green, after the fix | both pass: 16 distinct predecessors forming one contiguous chain, and the nanosecond row verifies after a fresh read |
| Reason tests | `aRewrittenRowIsReportedAsARowHashBreak` and `aMisLinkedRowIsReportedAsAChainBreak` assert the new distinction. The second one has to rewrite `row_hash` too: changing only `prev_hash` breaks the row's own digest first, which is `ROW_HASH` — a fork is a row that is internally consistent and still points at the wrong neighbour |

What changed:
- `AuditLogWriter.appendToChain` holds a `ReentrantLock` across the head read and the insert. `save` is transactional, so the row is committed before the lock is released and the next writer sees it. This serialises one JVM — the deployment this monolith has — and the Javadoc says so, because two instances would need a lock on the head row in the database rather than in memory.
- `AuditLog.onCreate` truncates `createTime` to microseconds *before* hashing, so the digest covers exactly what a read returns.
- `audit_log.create_time` is `TIMESTAMP(6) WITH DEFAULT CURRENT_TIMESTAMP(6)` (was `TIMESTAMP`). The column, not the hash, was the weak side: MySQL's plain `TIMESTAMP` keeps whole seconds, so microsecond hashes would still have failed there. `DevSchemaGuard.SCHEMA_VERSION` goes 2 → 3 and the reset path is the documented one: `rm -rf ~/.medical-dev/data` then restart.
- `verifyIntegrity` returns a `ChainBreak(rowId, ROW_HASH | CHAIN)` and `IntegrityReportVO` exposes it as `breakReason`. It checks the row's own digest before the link, so a changed field reports `ROW_HASH` and a self-consistent row pointing at the wrong predecessor reports `CHAIN`.

The legacy policy is the one the review asked for, minus the rewriting: **no historical digest is recomputed.** The pre-fix dev rows are forked and the only supported way forward is the schema reset, which the version bump forces anyway; after it, `breakReason` means an operator can read an old fork as history (`CHAIN`) and a `ROW_HASH` mismatch as a rewrite, instead of one undifferentiated "broken".

The test class clears `audit_log` in `@BeforeEach`: each test asserts on a chain of its own making, and one earlier broken row masks the break a later test injects. Production never deletes audit rows; this is fixture hygiene in the in-memory test database only.

### R53-09 — Serialize Observation effective time without appending seconds

**Location:** `module/patient/controller/FhirObservationController.java:117–119`.

**Observed:** an observation with effective time `2026-10-15T12:34:56` returns 500 from `GET /api/v1/fhir/Observation/{id}`. Appending `":00"` to `LocalDateTime.toString()` creates `2026-10-15T12:34:56:00` when seconds are already present.

**Suggested solution:**
- Use an explicit conversion/formatter with the intended clinical timezone and FHIR-compatible offset/precision. Do not patch the string by always adding another seconds segment.
- Use the same conversion for single-resource and search responses. Preserve the original instant/clinical time semantics, including fractional seconds where supported.

**Tests first:** extend `FhirIntegrationTest` with minute-only, nonzero-second and fractional-second observations. Both detail and search must return 200 and produce resources parseable by HAPI with the expected effective time. Add an explicit timezone/offset assertion.

**Fix record (R53-09) — done.** `buildFhirObservation` now formats the effective time with two explicit formatters instead of concatenating `toString()` with a seconds segment: seconds are always emitted, milliseconds are emitted when the row carries them.

| Check | Result |
|-------|--------|
| Red, before the fix | `fhirObservation_withNonZeroSeconds_shouldSerialiseEffectiveTime`: `Status expected:<200> but was:<500>` — `2026-06-04T08:00:30` became `2026-06-04T08:00:30:00` before HAPI ever saw it |
| Second red, on the first attempt | truncating to whole seconds made the request succeed but `fhirObservationDetail_shouldSerialiseEveryPrecisionToOneWellFormedTime` then failed with `expected: <2026-06-04T08:00:30.500> but was: <2026-06-04T08:00:30>` — a silent loss is not a fix |
| Green, after the fix | minute-only → `2026-06-04T08:00:00`, non-zero seconds → `2026-06-04T08:00:30`, fractional → `2026-06-04T08:00:30.500`; search and detail agree because both go through the same builder |
| Offset | none emitted, deliberately. `observation.effective_date` is wall-clock time with no zone, so a `Z` or an offset would assert a zone the data never had; FHIR allows a local dateTime. Recorded in `docs/API-LAYOUT.md` as the endpoint's contract rather than left implicit |

### R53-10 — Generate datetime-local bounds in local time

**Location:** `medical-web/src/views/appointments/index.tsx:121`.

**Observed:** at `2026-10-15 12:00` in America/Los_Angeles, `new Date().toISOString().slice(0, 16)` produces a minimum of `2026-10-15T19:00`. A valid 13:00 local appointment is therefore below the input's minimum.

**Suggested solution:**
- Format the minimum from local date/time components, not a UTC ISO string with the timezone suffix removed.
- Apply the bound only where a new/rescheduled time must be future; editing the status of an existing past-start appointment must remain possible after R53-06.
- Keep the API's timezone contract explicit; this UI fix must not silently reinterpret stored appointment times.

**Verification first:** a fixed-clock check at 12:00 America/Los_Angeles must allow 13:00 and reject 11:00 when creating an appointment. Include UTC and a positive-offset zone, and a browser check of editing an existing past-start appointment. Use existing tools or a small pure-function check; do not add a frontend test framework solely for this change.

**Fix record (R53-10) — done.** `<input type="datetime-local">` speaks the browser's wall clock while `toISOString()` speaks UTC, so the minimum was wrong by the viewer's offset in both directions. `medical-web/src/utils/datetime.ts` now owns the two conversions (`localDateValue`, `localDateTimeInputValue`) and every place that was pasting a UTC string into a local-time control uses them.

| Measured before the fix (same instant, three zones) | 19:30 in Los Angeles | 01:00 in Tokyo | UTC |
|---|---|---|---|
| `min` the input received | `2026-10-20T02:30` — **7 h in the future**, so a valid 19:45 could not be picked | `2026-10-19T16:00` — **9 h in the past** | `2026-10-20T02:30` |
| date-only default (`slice(0, 10)`) | `2026-10-20` while it was still the 19th locally | `2026-10-19` while it was already the 20th | correct |

UTC agreeing with itself in every column is why this survived: the bug is invisible on a UTC machine, which is where it was written.

After the fix, the compiled `datetime.ts` measured under `TZ=America/Los_Angeles`, `Asia/Tokyo` and `UTC` returns the local wall clock in all three (`2026-10-19T19:30`, `2026-10-20T01:00`, `2026-10-20T02:30`) — UTC is unchanged, which is the point of a fix that must not reinterpret stored times.

Scope: the same defect was in five date defaults that computed "today" in UTC (`patients` ×3 for a resolution/onset/administration date, the patient layout footer, `priorAuths` ×2, `prescriptions`' default prescription date). They are one bug class and one helper, so they were fixed together; each only changes which calendar day a default offers, never how a stored date is read.

Verification: `npm run check` — 0 errors, the same 4 pre-existing warnings; `tsc --noEmit` clean; no frontend test framework was added, per the finding. The picker bound is the only client-side gate — the server keeps its own rule (R53-06), so a client clock that lies cannot put a past appointment into the database.

### R53-11 — Ignore responses belonging to a conversation that is no longer selected

**Location:** `medical-web/src/views/chat/index.tsx:42–54` and `medical-web/src/views/patient/chat/index.tsx:30–43`.

**Finding / reproducible sequence to automate:** select conversation A, then B; resolve B's request first and A's request last. Both implementations unconditionally apply each response with `setMessages`, so A can replace B's messages while the header/recipient still points to B. This sequence was established from the code path; it was not exercised in a browser during this review.

**Suggested solution:**
- Guard responses with a selection/request generation, cancel obsolete requests, or use query state keyed by the full conversation identity. Staff identity includes both party type and ID.
- Apply the guard to both the first page and "Load earlier"; stale pages must not prepend another conversation's messages or overwrite totals.
- Reset pagination/error state on selection changes and ensure sending uses the same selected identity as the displayed conversation.

**Verification first:** use controlled deferred responses for A/B in both portals; resolve A last and assert only B's messages/total remain. Also switch conversations while loading older messages and while a request fails. Verify with browser network delay if no automated UI harness is available.

**Fix record (R53-11) — done.** Both chat views now carry a monotonically increasing load sequence: `loadMessages` captures `++loadSeqRef.current` before the request and applies the response only while it is still the newest, so a late reply for a conversation the user has left is dropped instead of replacing the panel. The guard is per view, covers the first page, "Load earlier" and `setMsgTotal` alike — one check at the point where the response would write.

| Check | Result |
|-------|--------|
| Staff chat (`views/chat/index.tsx`) | guarded; `selectPartner` still clears the panel immediately, so the header and the message list cannot disagree |
| Patient chat (`views/patient/chat/index.tsx`) | same guard, same placement |
| "Load earlier" | the page-2 request is invalidated by any newer load, including a conversation switch, so an old page cannot prepend into a different thread or overwrite its total |
| Verification | static: the response is the only writer and the sequence is bumped on every load; `npm run check` passes (0 errors). **Not** reproduced in a browser — the review established this from the code path, and the fix is a guard at that same path rather than new transport behaviour |

### Suggested implementation order and completion gates

1. Fix patient authentication state (R53-01 and R53-05), with separate regression evidence for revocation and failure-counter transactions.
2. Close the conflict-query disclosure (R53-02), updating the API response and frontend consumer together.
3. Fix resumable rotation (R53-03) before relying on completion status or retiring keys.
4. Fix and unit-test the production local JWT contract (R53-04); this requires no external IdP deployment.
5. Fix appointment completion and diagnosis propagation together (R53-06/07), then the related UI time bound (R53-10).
6. Fix audit timestamp canonicalization (R53-08) and FHIR time conversion (R53-09).
7. Fix both chat consumers' stale-response handling (R53-11).

For each slice: write the named regression scenario first, record the failing behavior, implement the fix, and record the passing result here. Run `mvn clean verify` for backend changes and `npm run check && npm run build` for affected frontend changes. Stop any instance using `target/classes` before rebuilding, then start a fresh isolated instance for live probes, as required by CLAUDE.md §8b.

Update `docs/API-LAYOUT.md` when token/session behavior, conflict response shape, status transitions or FHIR time contracts change. Update schema/version documentation if a chosen fix changes persisted columns. Do not mark an item complete based only on the existing 213-test suite. Close it only when its specific regression scenario passes and its evidence is recorded.

**Review verdict at the baseline: Blocked on the P1 findings. Fix status: 11/11 complete.** Every finding in this round has a red state recorded above and a regression that holds it: R53-01 to R53-05 (the P1s), R53-06/07 (visit completion and diagnosis propagation), R53-08 (audit chain), R53-09 (FHIR time), R53-10 (local-time bounds) and R53-11 (chat stale responses).


---

## Healthcare AI Agent enhancement — approved implementation plan (2026-10-19)

**Status: Rounds 54 and 55 complete (2026-10-19 → 2026-10-22) on the H2 + SQLite test environment; Rounds 56–59 remain Pending. PostgreSQL deployment is deferred by the 2026-10-22 scope decision and is not claimed as verified. The CI workflow was removed on 2026-10-22 (`fb8b4134`, "use local validation"); validation is local and no CI status is claimed.**

Reviewed source baseline: `b73f771124d3f1a906c7260f13560ee39d29c071` on master. This plan follows Round 53 and preserves its completion history. The planning review inspected source, diffs and committed test evidence; it did not rerun Maven. Implementers must establish a fresh passing baseline before changes.

### Scope and architecture decisions

Build an AI-assisted Patient Messaging Agent within the existing patient/staff workflow, using synthetic patient data for development, evaluation and demonstration. Spring Boot remains the authority for authentication, authorization, business data, message persistence, staff decisions and final publication. Python owns AI orchestration, retrieval and evaluation; it receives no MySQL credentials and cannot bypass the business layer.

- **AI service:** same-repository `medical-agent/`, Python 3.12, FastAPI, Pydantic, LangGraph; pyproject.toml, dependency lockfile, pytest, Ruff and type checking.
- **Provider:** DeepSeek API is the first real provider, alongside MockProvider. API key, base URL and model ID are environment configuration. Verify the selected model's actual structured-output/tool-calling behavior during integration; do not assume compatibility from an API label. Validate outputs with Pydantic; bounded retries or human fallback on invalid output. Keep the adapter replaceable; do not implement OpenAI/Anthropic in parallel initially. Do not hardcode prices or claim cost savings without measurements.
- **Storage:** retain MySQL for business state. Add one PostgreSQL instance for versioned knowledge and LangGraph checkpoints, with separate schemas/permissions. Retain Redis where the existing application needs it; introduce no new broker.
- **Delivery:** MySQL durable jobs and a Java polling dispatcher, with leases, attempts, deadlines and fencing generations. Java and Python are initially single-instance deployments.
- **RAG:** PostgreSQL full-text retrieval plus pgvector; lexical and dense baselines before RRF hybrid retrieval. Reranking is evidence-driven and deferred.
- **Human review:** all model-generated replies are draft-only initially. Administrative FAQ auto-response is a later, default-off gate. Clinical questions receive a routing summary for staff, not autonomous diagnosis or medication advice.
- **Tools:** first GetUpcomingAppointments and GetAppointmentStatus; appointment writes, medication changes and sensitive-record writes are excluded.
- **Observability:** basic structured events, timings and sensitive-data controls from the first slice; expand to OpenTelemetry and Langfuse in Round 59.
- **Frontend:** reuse React/TypeScript portals and messaging. First release is English and synthetic clinic policy content.
- **Deployment:** Docker Compose private/local demonstration. Real PHI, clinical deployment and any claim of legal compliance require a separate reviewed scope. Describe engineering controls as HIPAA-oriented, not certification.
- **Non-goals:** multi-agent architecture, model training, a second Java AI implementation, full FHIR bundles in model context, provider-availability promises, commercial clinical infrastructure and Azure deployment.

Python is chosen for the explicit AI-engineering goal and durable graph/evaluation ecosystem, accepting a second runtime, cross-service contracts, database operations and failure domain. It does not justify splitting the existing business monolith into microservices.

The existing CLAUDE.md single-backend/MySQL-only constraints need a narrowly documented exception during implementation: business modules remain a monolith; Python and PostgreSQL serve only the AI workflow. Preserve Round 52's dependency guard. Put message jobs/review/publication under `module/chat/`, appointment tool business logic under appointment, and shared authorization interfaces under `common/security`. Avoid a chat → agent → chat cycle and cross-module repository/entity imports.

### Changes already completed and not to repeat

| Existing work | Treatment in this plan |
|---|---|
| R53-02 / 647bf58: ConflictSlotVO removes patient details from conflict responses | Reuse the minimal DTO principle; do not reopen the disclosure fix |
| R53-06/07: historical visit completion and correct ICD propagation | Already fixed; not an Agent prerequisite task |
| R53-03 / e588402: full rotation scan, remaining verification and concurrent-update protection | Reuse; register every new encrypted AI field in rotation coverage |
| R53-08 / 5ccd2b8: JVM lock for audit append, timestamp canonicalization, break reasons | Single-JVM fix is complete; database locking is only required before multi-JVM deployment |
| R53-09/10: FHIR serialization and local date/time inputs | Preserve; define the new appointment tool's timezone contract explicitly |
| R53-11 / 94a9f2b: stale chat load response guards | Preserve; new review views need equivalent protection |
| b73f771: Round 53 closure | Leave historical records intact |

Occupied-slot lookup is not a complete availability engine: the current ±30-minute conflict search does not model working hours, leave, visit types and duration policy. GetProviderAvailability remains deferred. The existing getConversation marks messages read, so a future read-only GetMessageStatus must not reuse it.

### Implementation contracts

**Message intake and delivery**
1. Validate actor, receiver existence/enabled state and permitted communication relationship.
2. Deduplicate using clientMessageId scoped to sender identity; the same key with a different payload is a conflict.
3. Persist the message and, when AI is enabled, agent_run in one MySQL transaction.
4. Return after commit without waiting for Python, PostgreSQL or the model. AI downtime must not break human messaging.
5. Dispatch asynchronously from durable rows. SSE is an after-commit refresh hint, not a job queue; reconnect by database reconciliation.
6. Database failure must not be presented as successful delivery; preserve user input and allow retry.

**Authorization**
- Separate directional service credentials from short-lived task capabilities. Service authentication alone does not authorize access to patient records.
- Java creates a random opaque capability, stores its digest, and binds it to run ID, patient, allowed tools, expiry and execution generation.
- Every tool call and result submission checks the current account state, run state, capability, generation and ownership. Patient identity comes from server-owned run context, never a freely supplied model argument.
- A retry fences the old worker. Old capabilities cannot call tools or publish results.
- Keep credentials/capabilities outside prompts, traces and serialized checkpoints. Never share patient JWT signing keys with Python.
- Change DoctorPatientScope's missing/invalid authentication behavior to fail closed and regression-test existing authenticated callers. Do not masquerade as a doctor for machine calls.
- Define account-state lookup through module-owned implementations and shared interfaces; display-name lookup is not authorization.

**State and review**
- chat_conversation: canonical pair of typed party identities, revision, latest message ordering.
- agent_run: message/input version, state, lease, attempt, nextAttemptAt, deadline, execution generation, idempotency and pinned workflow versions.
- agent_result: validated classification, risk, encrypted draft, source references, model/prompt/KB/schema versions.
- message_review: queue/claim, reason, expected conversation/result version, reviewer, decision and edited response.
- agent_action_event: allow/deny/failure, review/publication and recovery events with stable event IDs; durable business evidence, not merely best-effort traces.
- Core run path: PENDING → RUNNING → WAITING_REVIEW → COMPLETED. Exceptional terminals: FAILED, CANCELLED, SUPERSEDED. Review completion and run completion are distinct.
- Java commits the approval/edit and final message publication atomically; repeated approvals publish once. A changed conversation or intervening human reply invalidates an old draft.
- Staff can publish while Python is down. Java later reliably delivers the review outcome to resume/finish the graph. Graph resume must not resend the already-published reply.
- Persist draft/review creation idempotently: LangGraph resumes can re-execute the beginning of an interrupted node. Checkpointing is not exactly-once business delivery.
- MySQL is business authority; PostgreSQL is execution state. Use retries and reconciliation, not a cross-database transaction.
- Route initially to the original valid staff receiver; ADMIN can assign. Do not introduce a new nurse role/team hierarchy. Preserve established valid communication relationships; new conversations select from a server-authorized recipient list.

**AI safety and data**
- Classification schema: intent, secondaryIntents, riskLevel, recommendedAction, reasonCodes, evidenceSpans, abstain, schemaVersion. Cover General FAQ, Appointment/Scheduling, Billing/Insurance, Medication, Clinical, Urgent/Emergency and Other/Unknown.
- Risk is independent of intent. A billing question with urgent indicators must retain urgent routing.
- No model-reported confidence probability in v1. Invalid enums, extra fields, invalid evidence spans and malformed output cannot reach publication.
- Deterministic safety rules cannot be downgraded by the model. Unclear intent, invalid output, insufficient evidence, clinical advice and tool failures go to staff.
- Display a fixed, reviewed emergency/help notice without waiting for the model; the channel does not promise real-time emergency response. Rule misses are not evidence of safety. Synthetic clinical demonstrations are not clinically validated triage.
- Treat all patient text, retrieval documents and tool output as untrusted data; none may grant permissions or change the tool allowlist.
- Only approved synthetic clinic policies in the initial KB. Do not embed patient conversations or medical records into the public-policy index.
- Ingest a manifest with document ID, version, effective date, approval state and content hash; title-aware chunking; idempotent ingestion; explicit release/withdrawal.
- Construct citations from retrieved server-known IDs. Source existence alone does not establish claim support. Missing/conflicting/withdrawn evidence triggers fallback.
- Pin prompt/model configuration, schema and KB release per run; recheck source withdrawal and conversation freshness before publication.
- Encrypt sensitive drafts/review text and include new columns in key rotation. Checkpoints, backups, embeddings and eval fixtures have explicit data-retention treatment. Prefer references over copying full conversations into checkpoints.
- Never export raw PHI, JWTs, capabilities or unrestricted prompt/tool bodies through telemetry. Verify third-party instrumentation with sensitive canaries.

### Proposed files and API surface

| Location | Responsibility |
|---|---|
| medical-agent/pyproject.toml and dependency lockfile | Python runtime and checks |
| medical-agent/src/medical_agent/api/ | Run, resume, status and health endpoints |
| medical-agent/src/medical_agent/schemas/ | Classification, draft, tool and execution schemas |
| medical-agent/src/medical_agent/graph/ | State, nodes, conditional routing and recovery |
| medical-agent/src/medical_agent/providers/ | MockProvider and DeepSeekProvider |
| medical-agent/src/medical_agent/rag/ | Ingestion, retrieval, citation and version control |
| medical-agent/src/medical_agent/tools/ | Spring client and allowlisted tool registry |
| medical-agent/tests/ and medical-agent/evals/ | Deterministic tests, datasets and real-provider evaluation |
| Java module/chat/ | Conversations, jobs, results, reviews and publication |
| Java module/appointment/ | Minimal appointment read DTOs/services/controllers |
| Java common/security/ | Shared internal authorization contracts |
| medical-web/src/views/chat/reviews/ | Staff review queue and detail |
| contracts/agent/ | Versioned JSON Schema and cross-language fixtures |
| compose.yaml | Reproducible runtime (a GitHub Actions workflow existed briefly; the owner removed it in `fb8b4134` for local validation) |

| Proposed endpoint | Caller / behavior |
|---|---|
| Python POST /internal/v1/runs | Java dispatcher starts/continues a fenced execution |
| Python GET /internal/v1/runs/{runId} | Java reconciliation; authenticated, scoped status |
| Python POST /internal/v1/runs/{runId}/resume | Java delivers persisted staff decision |
| Java POST /api/v1/internal/agent/runs/{runId}/results | Python submits classification/draft idempotently; no patient-send authority |
| Java GET /api/v1/internal/agent/appointments/upcoming | Task-scoped patient query, bounded horizon/count |
| Java GET /api/v1/internal/agent/appointments/{id}/status | Task scope plus appointment ownership |
| Java GET /api/v1/message-reviews | Staff-authorized queue |
| Java POST /api/v1/message-reviews/{id}/decision | Approve-and-send/edit/reject with expectedVersion and idempotency |

Schemas must be validated on both sides. Keep existing Result<T> conventions for Java and document status/error semantics. Do not expose internal endpoints through general patient privileges.

Database evolution is part of Round 54.2: introduce a versioned baseline and upgrade path, recommended Flyway for Java/MySQL and independent versioned PostgreSQL migrations for Python-owned tables. Coordinate Spring SQL initialization and DevSchemaGuard rather than allowing two schema owners. Test fresh installation and upgrade from the reviewed schema. Do not enable blind baselining or make data deletion the normal upgrade path. Manage LangGraph checkpoint setup against locked dependency versions.

## Round 54 — Messaging foundation (Done: 54.1–54.4)

**Goal:** reliable human messaging before AI integration. **Estimate:** 28–44 hours. Depends on a fresh baseline test run and reviewed migration/contract design.

| Slice | Priority / status | Work and files | Tests first / acceptance |
|---|---|---|---|
| 54.1 Identity and recipients | P0 / **Done** (2026-10-19) | DoctorPatientScope fail-closed; ChatService receiver checks (staff and patient account state, symmetric conversation rule); fix typed-party grouping; module-owned account/relationship checks | MessagingAuthorizationIntegrationTest: no identity rejected; invalid/disabled receiver rejected; patient/staff sharing a numeric ID remain distinct; existing conversation authorizes both directions; portal account state gates delivery; authenticated existing flows preserved — 31/31 pass, 19 were red before the two fix passes (see 54.1 record below) |
| 54.2 Conversations and migration | P0 / **Done** (2026-10-19, after two review rounds) | chat_conversation entity/repository/service; canonical typed pair; historical backfill; real database conversation pagination and revision; a v3→v4 upgrade the application performs itself; one normalised clock for messages and threads | ChatConversationIntegrationTest + SchemaUpgradeIntegrationTest + DevSchemaGuardTest: >20 active messages do not hide older conversations; stable tie ordering; backfill preserves message count/ownership and agrees with live recording; clean install and a real startup on a previous-release database with its own history; a real restart; concurrent first messages all persist; a late older message never rewinds a thread — 12 red→green across the passes (see the 54.2 records) |
| 54.3 Idempotent sending and UI recovery | P0 / **Done** (2026-10-21, after two review rounds) | Message DTO/entity/repository and both chat views: clientMessageId, server ID replacement, failed state and preserved input; schema v5 with a guard step that adds the column to an existing database, resumable from a partial upgrade | MessageIdempotencyIntegrationTest: duplicate returns same message; changed payload conflicts; concurrent repeats collapse to one and move the thread once; UI retry retains content, never stacks bubbles, does not land in another selected conversation, and a page-one refresh after a send does not skip a page — chatState.test.ts holds the UI rules, 13/13 (see the 54.3 records) |
| 54.4 After-commit notification and safety notice | P0 / **Done** (2026-10-21, after review) | ChatEventListener after-commit behavior; fixed help notice and versioned deterministic safety entry point | ChatCommitEventIntegrationTest: rollback produces no push (including a rollback after the callback was registered); notification failure does not roll back message; safety notice is independent of model availability and stays on screen when its request fails — 6/6 pass, 3 red first (see the 54.4 records) |

Preserve the completed stale-response guards. Keep new-conversation selection server-authorized. Specify how revision increments serialize messages and review publication; do not rely on browser timestamps.

### Round 54.1 — Identity and recipients (Done, 2026-10-19)

**Source baseline:** `e0eb770` on master, clean working tree. **Recorded test baseline (this round):** `mvn clean test` → **245 tests, 0 failures, BUILD SUCCESS**. (CLAUDE.md's "204 tests" line is stale; the number above is the measured one.) No instance was running against `target/classes` — `curl` on `:8080` refused the connection — so `clean` was safe.

**What the source actually had (verified by reading it, not by trusting the plan):**

1. `DoctorPatientScope.resolve()` returned `null` — the code for "ADMIN, no filter" — whenever `Authentication` was missing or the principal was not a `LoginUser`. Any identity the security context could not name was therefore read as "every patient". A logged-in principal with no `ADMIN`/`DOCTOR` role fell through to the provider branch, so patient *N* resolved to the patient list of doctor *N* (same reason R2-1 exists on the message side).
2. `ChatService.sendMessage(senderId, senderType, receiverId, receiverType, content)` persisted whatever it was handed: no receiver type check beyond the staff controller's, no existence check, no deleted/disabled check, no relationship check — a patient could address any numeric staff id, and a doctor any patient. The sender was a controller argument rather than the verified context. Only two callers existed (`ChatController`, `PatientChatController`); no background job or scheduler reached either `resolve()` or `sendMessage` (checked: `AppointmentScheduler`, `QualityScheduler`, `DataRetentionJob`, `KeyRotationService`, `ChatEventListener` touch neither).
3. `getConversations()` decided "this message is mine" with `m.getSenderId().equals(currentUserId)` — a bare id across two independent id spaces — so an inbound message from staff 103 was filed as patient 103's own outgoing message and the patient was shown a conversation with themselves.

**Red state (first run of the new class, before any production change):** `mvn test -Dtest=MessagingAuthorizationIntegrationTest` → `Tests run: 22, Failures: 14`. Representative lines, verbatim:

```
noSecurityContext_shouldNotResolveToUnboundedScope:105 missing authentication must not mean 'no filter' ==> Expected BusinessException to be thrown, but nothing was thrown.
patientPrincipal_shouldNotInheritTheDoctorWithTheSameId:136 Expected BusinessException to be thrown, but nothing was thrown.
staffToPatient_outsideScope_shouldRejectAndPersistNothing:219 Status expected:<403> but was:<200>
staffToPatient_missingPatient_shouldRejectAndPersistNothing:229 Status expected:<404> but was:<200>
patientToUnrelatedStaff_shouldRejectAndPersistNothing:280 Status expected:<403> but was:<200>
patientToMissingStaff_shouldRejectAndPersistNothing:301 Status expected:<404> but was:<200>
patientConversations_shouldNotInventAConversationWithItself:343 patient 103 was shown a conversation with patient 103 ... expected:<false> but was:<true>
staffConversations_shouldNotInventAConversationWithItself:361 ... expected:<false> but was:<true>
```

The remaining 8 tests in that class (ADMIN/DOCTOR/emergency controls, in-scope sends, staff-to-staff, typed ownership) passed on the same run, which is what makes the 14 failures attributable to the three defects rather than to a broken harness. Two service-boundary tests were written against the 5-argument signature for the red run and moved to the new 3-argument signature in the same commit as the fix, since the sender argument itself is what the slice removes.

**Changes:**

| File | Change |
|---|---|
| `common/security/DoctorPatientScope.java` | `resolve()` fails closed: no/invalid `Authentication`, a non-`LoginUser` principal, or a non-positive user id → 401; an authenticated identity without `ADMIN`/`DOCTOR` → 403. `null` ("no filter") is now reachable only for `ADMIN`. Adds `isRelated(staffId, patientId)` — the same provider-backed relationship, asked about a named account rather than the current one |
| `common/security/StaffAccountState.java` | New shared interface: `isActive(staffId)` = account exists, not soft-deleted, enabled. Deliberately separate from `StaffLookup`, which is display-only — "the name resolved" is not an authorization answer |
| `module/system/service/SysUserAccountState.java` | The system module's implementation, reading its own repository (`@SQLRestriction` supplies the deleted half, `EnabledStatus` the enabled half) |
| `module/chat/service/ChatService.java` | `sendMessage(receiverId, receiverType, content)` derives the sender from the security context (401 when absent, both/neither identity domain → 403) and validates the pair before persisting: receiver type, self-send, patient existence, staff active state, doctor scope / emergency scope for staff→patient, care relationship or existing thread for patient→staff. `getConversations()` compares party type *and* id |
| `module/chat/repository/MessageRepository.java` | `countBetween` + `hasConversation` default method, for "these two typed parties already have a thread" |
| `module/chat/controller/ChatController.java`, `PatientChatController.java` | Stop passing sender id/type; validation stays in the service |
| `src/test/java/com/example/medical/MessagingAuthorizationIntegrationTest.java` | New: 22 integration tests (below) |
| `src/test/resources/cleanup-test-data.sql` | Removes the two probe staff accounts so a failed run cannot leak them into another class |
| `docs/API-LAYOUT.md` | Chat recipient rules, error codes, typed conversation matching, fail-closed scope note |

**Business rule, decided (Round 54.1 review):** the code had no rule at all, so one was derived from what it does define, and the review fixed its two inconsistencies. A message is accepted when the receiver is deliverable **and** one of these holds:

- **Established conversation** — the two typed parties already exchanged a message. This authorizes **both directions identically**, so a thread whose appointment or prescription has since ended stays two-way (the first implementation let the patient reply and refused the doctor, which made such a thread one-way).
- **Care relationship (patient → staff)** — an appointment or prescription through a module-owned provider. This is deliberately not "appointments only": a prescription relationship counts.
- **Clinical access (staff → patient)** — the sender's patient scope contains the patient (`DOCTOR` by relationship, `ADMIN` unscoped, emergency break-glass token for that patient).

Deliverability always applies: the patient record must exist and its portal account must exist, not be soft-deleted and be enabled (`PatientPortalAccountState`); a staff receiver must exist and be enabled (`StaffAccountState`). Lockout is **not** a disabled account and does not block delivery.

Two properties this rule deliberately keeps apart: an existing conversation never widens the clinical scope (a doctor may reply in a thread and still gets `403` on that patient's records — `existingConversation_shouldNotWidenClinicalRecordAccess` holds it), and a doctor with no relationship and no thread still cannot contact a patient. Consequences worth knowing, both intentional: a patient cannot *start* a thread with an `ADMIN` (no care relationship, no thread yet — but an `ADMIN` can start one, and the patient can then reply), and staff-to-staff stays as it was (any active account), since the practice has one role hierarchy and no org-unit data to scope by.


### Round 54.1 review fixes (second pass, 2026-10-19)

Three findings from the review were re-checked against the source; all three held.

**1. P1 — an existing conversation was one-way.** `requirePermittedConversation` let the patient reply on the strength of a thread but required `doctorPatientScope.requireAccess()` for the doctor, so a thread that outlived its appointment could be answered by the patient and refused to the doctor. Fixed by evaluating one shared relationship clause for both directions: **established conversation OR the direction's own care rule** (care relationship for patient → staff, clinical access for staff → patient). The clinical scope is now only *read* through a new `DoctorPatientScope.permits(patientId)`; nothing about messaging is merged into it, so record access is unchanged.

**2. P2 — the patient receiver's portal account was never checked.** `patientLookup.exists()` proved the clinical record existed, not that a mailbox did. Delivery now also requires `PatientPortalAccountState.isActive(patientId)` — a new shared interface implemented by the patient module (`PatientAuthAccountState`) against its own `patient_auth` row: exists, not soft-deleted, status enabled. Lockout is excluded on purpose and held by a test, so a temporary lock cannot be conflated with a disabled account. The patient record check stays.

**3. P2 — the colliding-thread tests depended on method order.** Three tests re-seeded the same `PATIENT:103 ↔ STAFF:103` pair without per-test isolation, one asserted `unreadCount == 1`, and `@AfterAll` deleted by bare `sender_id/receiver_id = 103` — which is precisely the number the two identity domains share. Fixed: every created message id is recorded and deleted in `@AfterEach`; the pair is emptied by a **typed** both-columns predicate before seeding; created patients, their appointments and portal rows are removed per test; cleanup SQL also clears orphan portal rows (patient ids restart at 104 per class). Nothing was loosened and no ordering was pinned.

**Red evidence for the second pass** (`mvn test -Dtest=MessagingAuthorizationIntegrationTest`, before the code changes):

```
Tests run: 30, Failures: 5
existingConversation_withoutCareRelationship_shouldLetBothSidesReply:495  Status expected:<200> but was:<403>
existingConversation_shouldNotWidenClinicalRecordAccess:525             Status expected:<200> but was:<403>
staffToPatient_disabledPortalAccount_shouldRejectAndPersistNothing:436  Status expected:<404> but was:<200>
staffToPatient_softDeletedPortalAccount_shouldRejectAndPersistNothing:451 Status expected:<404> but was:<200>
staffToPatient_missingPortalAccount_shouldRejectAndPersistNothing:463    Status expected:<404> but was:<200>
```

Rejection tests now assert three things each: the typed pair's row count is unchanged, no message row was added, and **no `NewMessageEvent` was published** (a synchronous `ApplicationListener` registered on the context records them; an accepted send asserts exactly one, so the "no event" assertions are not vacuous). `adminToPatientWithoutCareRelationship_shouldStillSend` keeps the pre-54.1 ADMIN flow alive, since `permits()` is unscoped for ADMIN. 54.4 and its after-commit delivery remain untouched and Pending.

**Files changed in the second pass:** `common/security/DoctorPatientScope.java` (`permits`), `common/security/PatientPortalAccountState.java` (new), `module/patient/service/PatientAuthAccountState.java` (new), `module/chat/service/ChatService.java` (shared relationship clause, portal-account gate), `MessagingAuthorizationIntegrationTest.java` (fixtures, event recorder, 8 new tests), `cleanup-test-data.sql` (orphan portal rows), `docs/API-LAYOUT.md`, this record.

**Verification (actually run):**

- First pass: `mvn test -Dtest=MessagingAuthorizationIntegrationTest` → `Tests run: 22, Failures: 14` before the fix (lines above), `22/0` after; `mvn clean verify` → **267 tests, 0 failures, BUILD SUCCESS**.
- Second pass red → green: `Tests run: 30, Failures: 5` → `Tests run: 30, Failures: 0, Errors: 0`; adding the ADMIN control test brings the final class to 31 tests, `31/0`.
- Method-order independence, measured with JUnit's random orderer and five fixed seeds — `-Djunit.jupiter.testmethod.order.default='org.junit.jupiter.api.MethodOrderer$Random' -Djunit.jupiter.execution.order.random.seed=<1|7|2026|3|11>` — every run `Failures: 0, Errors: 0`, and the seeds produced different first tests (seed 1 on the final 31-test class: `authenticatedStaffReads_shouldKeepWorking`; earlier runs: `patientPrincipal_shouldNotInheritTheDoctorWithTheSameId`, `patientToMissingStaff_shouldRejectAndPersistNothing`, `patientToAnotherPatient_shouldRejectAtTheServiceBoundary`), so the pass is not an artifact of one order.
- `mvn clean verify` (backend gate, after the second pass, final tree) → **276 tests, 0 failures, 0 errors, BUILD SUCCESS** (245 before this round + 31 new); `LayeringGuardTest` green with 0 repository references, 0 entity references, 0 import cycles and no allowlist (the two new shared types are `common/security` interfaces implemented by the owning modules).
- No frontend change was needed or made: both chat views already treat a rejected send as a failed optimistic message, and no request/response field changed shape. The development database was not touched; every run used the in-memory `jdbc:h2:mem:medical_test` profile.

**Coverage of the acceptance list:** no valid identity cannot obtain unbounded scope (3 tests, plus 401/403 codes asserted); ADMIN/DOCTOR/emergency behaviour preserved (3 tests, HTTP and direct); missing/deleted/disabled receivers rejected (staff and patient, including a disabled, soft-deleted or absent portal account) with the typed pair count asserted unchanged; permitted sends still work (staff→in-scope patient, patient→related doctor, staff→staff, and both directions of a thread whose care relationship has ended); patient and staff sharing numeric id keep correct counterpart and message ownership in both directions (3 tests); every rejection asserts no row **and** no `NewMessageEvent`.

**Limits of this slice (not claimed):** the conversation list still reads the newest 20 messages and groups in memory — real conversation pagination, tie ordering and the `chat_conversation` table are 54.2. `clientMessageId` idempotency is 54.3, after-commit notification and the fixed safety notice are 54.4. `getConversation` (the read path) still has no scope check of its own: it can only return messages the caller is a party to, and tightening it is not part of 54.1 — recorded here so it is not mistaken for done. Rejection is proven not to publish `NewMessageEvent` (a context listener records them, and an accepted send asserts exactly one), but the event is still published inside the transaction and pushed to SSE without an after-commit step — that ordering belongs to 54.4, which stays Pending. Loopback/SSE delivery itself is not asserted here.

### Round 54.2 — Conversations and migration (Done, 2026-10-19)

**Source baseline:** `8c5f58be` (Round 54.1 complete), clean tree. **Recorded test baseline (this round):** `mvn clean verify` → **276 tests, 0 failures**. Runs on the in-memory `jdbc:h2:mem:medical_test` profile; the development database was not touched.

**What the source actually had:** `ChatService.getConversations` loaded the newest **20 messages** (`PageRequest.of(0, 20)`) and grouped them in memory, so a thread with more than 20 messages hid every quieter thread, `page`/`size` paginated that in-memory list rather than the threads, and ordering was "first appearance in the message window" with no tie-break. There was no conversation entity, no revision, and no way to address a thread without scanning messages. (Verified in source; not assumed from the plan.)

**Red state (before any production change):** `mvn test -Dtest=ChatConversationIntegrationTest` → `Tests run: 2, Failures: 2`, both through the public endpoint:

```
busyThread_shouldNotHideAnOlderOne:50  patient 101's thread must still be listed after 22 newer messages in another thread ==> expected: <true> but was: <false>
pages_shouldCoverEveryThreadOnce_andStayStable:72  doctor1 has exactly three threads here: [PATIENT:100] ==> expected: <3> but was: <1>
```

**Changes:**

| File | Change |
|---|---|
| `resources/sql/schema.sql` | `chat_conversation`: canonical typed pair with `uk_chat_conversation_pair`, `last_message_id/time`, `message_count`, `revision`, plus one index per party side; `DevSchemaGuard.SCHEMA_VERSION` 3 → 4 |
| `module/chat/service/Party.java` | Typed party value with the canonical ordering (`type`, then `id`) the pair relies on; replaces the private record in `ChatService` |
| `module/chat/entity/ChatConversation.java` | Entity over the new table (`BaseEntity` rules: soft delete, version) |
| `module/chat/repository/ChatConversationRepository.java` | Pair lookup accepting either orientation, a paged fetch for one party ordered `lastMessageTime DESC, id DESC`, and the incrementing `recordMessage` update |
| `module/chat/service/ChatConversationService.java` | Canonicalises the pair, increments an existing thread in one statement, creates the row for a first message |
| `module/chat/service/ChatConversationBackfill.java` | Startup backfill: one ordered scan of history → one row per thread with no row yet; idempotent; plain JDBC so it can run against a standalone database in tests |
| `module/chat/service/ChatService.java` | `sendMessage` records the thread in the same transaction; `getConversations` is now a paged database query with partner/unread/last-message assembly |
| `module/chat/repository/MessageRepository.java` | Adds one grouped unread query for the whole page; drops the now-unused 20-message window and per-row unread count |
| `src/test/java/com/example/medical/ChatConversationIntegrationTest.java` | New: 5 tests (below) |
| `src/test/resources/cleanup-test-data.sql` | Removes conversation rows that name a party no test owns any more |
| `docs/API-LAYOUT.md`, `README.md` | Conversation list semantics, schema version note, startup backfill, MySQL manual-DDL note |

**Decisions and their evidence.**

- **Schema evolution stays on the repository's existing mechanism** (`schema.sql` + `spring.sql.init` + `DevSchemaGuard`), not Flyway. Asked and decided in this round: there is no MySQL instance here to verify a migration framework against, Round 50 M-series recorded migration tooling as out of scope for an H2-only demo, and adopting Flyway for part of the schema while `spring.sql.init` still created tables would leave two schema owners — the outcome the plan forbids. Flyway is therefore an **open infrastructure item**, not part of 54.2's completion: MySQL deployments currently apply the new DDL by hand (documented in `README.md`).
- **Upgrade path, tested rather than asserted.** `schemaScript_shouldServeACleanInstallAndUpgradeAnOlderDatabase` builds two standalone H2 databases: one empty (clean install → table present, canonical pair enforced as a unique constraint), and one holding only the reviewed v3 `message` table with history (upgrade → the schema step adds the table, the backfill creates exactly one thread with `message_count` 3, `revision` 3, the patient first in the canonical pair, and the message rows untouched).
- **Revision is internal for now.** It increments per recorded message and is the anchor later work (57.3 freshness) will read; exposing it through `ConversationVO` was not needed by any caller, so the API shape is unchanged.
- **Tie ordering is decided, not left to the database:** `ORDER BY last_message_time DESC, id DESC`.

**Verification (actually run):**

- Red → green on the public endpoint: `Tests run: 2, Failures: 2` → `Tests run: 5, Failures: 0, Errors: 0` for `ChatConversationIntegrationTest`.
- Method-order independence: the class passes under JUnit's random orderer with seeds 1, 5 and 42 (`-Djunit.jupiter.testmethod.order.default='org.junit.jupiter.api.MethodOrderer$Random' -Djunit.jupiter.execution.order.random.seed=<seed>`) → `Tests run: 5, Failures: 0` each. The fixtures create their own patients, portal accounts and appointments and delete them per test; threads on the **seeded** patients are deliberately left as history so a later class still finds the seeded conversations it asserts on.
- `mvn clean verify` → **281 tests, 0 failures, 0 errors, BUILD SUCCESS**; `LayeringGuardTest` green with 0 repository references, 0 entity references, 0 import cycles and no allowlist (the new types are all inside `module/chat`).
- Round 54.1's suite still passes unchanged in behaviour; its fixtures were updated to run the backfill after writing raw history, because the conversation list is now driven by conversation rows rather than by a message scan.

**Limits of this slice (not claimed):** no migration tool (see the decision above) and no MySQL-executed DDL — the upgrade path is proven on H2 in MySQL mode only. Deleting a message does not re-point or shrink its conversation row (the product has no message deletion; only tests remove rows, and they remove the thread with them). A conversation row is not removed when both its parties disappear, beyond the test-data cleanup. The startup backfill inserts missing threads only; it never rewrites an existing row, so a corrupt revision would need a separate repair. `clientMessageId` idempotency (54.3) and after-commit notification (54.4) remain Pending, and the list still returns at most `Pages.MAX_SIZE` rows per page with no cursor.

### Round 54.2 review fixes (second pass, 2026-10-19)

Three findings from the review of `bc2afdd` were re-checked against the source; all three held, and each now has a regression that failed before the fix.

**1. P1 — a v3 database could not start.** `DevSchemaGuard` rejected any version mismatch, so a database whose data was fine was told to delete itself, and the startup backfill (which runs on `ApplicationReadyEvent`) never got the chance to run. Fixed by making the guard an **upgrader**: a database one version behind is carried forward when the step between the versions is additive *and the table that step adds is really present* — the guard checks `INFORMATION_SCHEMA` before recording, so "upgraded" is never a blind stamp. Unknown older versions, a missing table, and a database newer than the build still fail loudly with the reset guidance. `SCHEMA_VERSION` is now public because the upgrade test asserts against it.

**2. P2 — a simultaneous first message lost one side.** `ChatConversationService` looked the thread up and inserted when it was absent, so two concurrent first messages both inserted; the pair's unique key rejected one, and its already-written message went with the transaction. Fixed with a single create-or-update statement (`INSERT … ON DUPLICATE KEY UPDATE`, supported by MySQL and by H2 in MySQL mode) that also un-deletes a thread a new message revives; counting and pointing at the last message are separate statements, so a retry has nothing to poison.

**3. P2 — an older message recorded later rewound the thread.** The counter update unconditionally overwrote `last_message_id`/`last_message_time`. The pointer is now a separate guarded `UPDATE` that moves forward only, ordered by `(time, messageId)` — the same rule the backfill applies to history. Time is **truncated to whole seconds**, the precision MySQL's `TIMESTAMP` keeps, so the value stored, the value compared and the backfill's reading of `message.create_time` cannot disagree; messages inside one second are ordered by id, which the tie-break already used for threads.

**Red evidence for the second pass** (`mvn test -Dtest='SchemaUpgradeIntegrationTest,ChatConversationIntegrationTest'` before the code changes):

```
SchemaUpgradeIntegrationTest (both tests): ERROR — IllegalStateException: Local database schema is v3
    but this build expects v4 … Delete the local database file (default: ~/.medical-dev/data/, or the
    path in H2_DB_PATH) and restart to rebuild schema + seed data.
concurrentFirstMessages_shouldAllPersistInOneConversation: ERROR —
    DataIntegrityViolationException: Unique index or primary key violation: "…UK_CHAT_CONVERSATION_PAIR…"
lateOlderMessage_shouldNotRewindTheThread:308 — a late-arriving older message must not move the thread's
    last-message pointer back ==> expected: <632> but was: <631>
```

**Tests added for the second pass:** `SchemaUpgradeIntegrationTest` boots the **real application** against a database prepared as the previous release left it (every table except `chat_conversation`, `schema_version` row `3`) and asserts the startup path upgrades it: version 4 recorded with v3 kept in the history, both seeded threads backfilled with their counts and revisions, all eight seeded messages intact, and a following guard run a no-op. `ChatConversationIntegrationTest` gained `concurrentFirstMessages_shouldAllPersistInOneConversation` (eight simultaneous senders on a brand-new pair: all messages stored, one thread, count and revision 8) and `lateOlderMessage_shouldNotRewindTheThread` (an older message recorded after a newer one must not move the pointer; equal timestamps fall back to the higher id in both arrival orders; the stored thread time is the column's precision). `DevSchemaGuardTest` now covers the upgrade, the missing-table failure and an older version this build cannot complete.

**Verification (actually run):** `mvn clean verify` → **287 tests, 0 failures, 0 errors, BUILD SUCCESS** (281 before this pass + 6 new); `LayeringGuardTest` 4/4 with no allowlist. The two changed classes pass under JUnit's random orderer with seeds 2 and 9 (`Tests run: 9, Failures: 0` each).

**Still not claimed:** the upgrade path is proven on H2 in MySQL mode only — no MySQL instance exists here, and MySQL never runs `sql/schema.sql`, so it still needs the DDL applied by hand (documented in `README.md`). The guard upgrades one additive step (v3→v4); an older database, or a step that would alter an existing table, still requires the reset. Thread ordering is second-precision, so two threads active within the same second are ordered by conversation id. Thread writes now go through SQL (create-or-update, then the guarded pointer), so the row's `version` column is not used for optimistic locking — reads still honour the entity's soft-delete restriction, and nothing updates a thread through the entity.

### Round 54.2 review fixes (third pass, 2026-10-19)

Three findings from the review of `08c15d11` were re-checked; all three held.

**1. P2 — messages and threads still kept two clocks.** The thread time was truncated to whole seconds, but `Message.createTime` still came from `BaseEntity`'s nanosecond `LocalDateTime.now()`, and MySQL *rounds* fractional seconds into the column's second while the thread was floored — so one message could be stored a second later than the thread that points at it. The backfill made it worse by ordering the scan with the database's own fractional reading and only then truncating, while the live path compared truncated values. Fixed by normalising the clock **before the message is inserted** (one `storedPrecision(now)` value written to the message row and handed to the thread) and by ranking history in the backfill with the same `(stored second, message id)` rule the live pointer uses. `BaseEntity` is untouched, so audit timestamps keep their precision.

**2. P2 — the upgrade check could be satisfied by another schema's table.** `INFORMATION_SCHEMA.TABLES` lists every schema the login can see, so on MySQL a `chat_conversation` in another catalog would let the guard record v4 while the application's own schema still lacked it. The check now goes through JDBC metadata scoped to the connection's catalog/schema, which behaves the same on MySQL and H2.

**3. P2 — the upgrade test did not prove "old data survives" or "restart".** Its eight messages were created by this build's own seed step during the boot under test, and its second test only re-ran the guard. The test now plants **its own history before the new build ever runs** (explicit ids 900/901, content, author and recipient on a pair the seed step does not touch) and asserts each row still exists with its id, content and ownership, that the planted pair became one thread with the right count, revision and last message, and that the seeded threads are unchanged. The second test now performs a **real restart** — a second application context on the same database, running the same SQL initialization, guard and backfill — and compares a snapshot (version, version rows, message count, planted rows, thread count, sum of counts and revisions) before and after.

**Red evidence for the third pass** (`mvn test -Dtest='DevSchemaGuardTest,ChatConversationIntegrationTest'` before the code changes):

```
messageAndThread_shouldShareOneClock:352 — the message is written at the precision the thread is compared
    at … ==> expected: <0> but was: <210564000>
backfillAndLiveRecording_shouldChooseTheSameLastMessage:385 — the backfill must pick the same last message
    the live path picks ==> expected: <660> but was: <659>
failsFastWhenOnlyAnotherSchemaHasTheTable:102 — Expected java.lang.IllegalStateException to be thrown,
    but nothing was thrown.
```

**Verification (actually run):** `mvn clean verify` → **290 tests, 0 failures, 0 errors, BUILD SUCCESS** (287 before this pass + 3 new); `LayeringGuardTest` 4/4 with no allowlist. The three changed classes pass under JUnit's random orderer with seeds 4 and 17 (`Tests run: 18, Failures: 0` each). Order within the class no longer matters for the restart test either: the restarted context re-runs the guard and backfill over the already-upgraded database.

**Still not claimed:** MySQL is still unverified for the DDL itself (no instance here; MySQL does not run `sql/schema.sql`, so the table and the manual `DevSchemaGuard` step remain the deployment path). Whole-second thread ordering means threads active inside the same second are ordered by conversation id. The backfill still only inserts missing threads; a corrupt revision would need a separate repair.

### Round 54.3 — Idempotent sending and UI recovery (Done, 2026-10-21)

**Source baseline:** `b62aae15` (54.2 complete after three review passes), clean tree. **Recorded test baseline (this round):** `mvn clean verify` → **290 tests, 0 failures**. No instance was running against `target/classes`.

**What the source had:** no send carried an idempotency key. A retry after a timeout stored a second message and bumped the thread's count and revision twice; a double click did the same. Both chat views cleared the composer before the request and, on failure, **removed the optimistic message**, so the typed text was gone and there was nothing to retry. The patient view had no error state at all.

**Design.**
- `message.client_message_id` (nullable, ≤100 chars) with a unique index `(sender_type, sender_id, client_message_id)`: the key is scoped to the sender's **typed** identity, so staff 2 and patient 100 may reuse the same string, and nulls are distinct in both MySQL and H2, so clients without a key keep the old behaviour.
- `ChatService.sendMessage` is idempotent when the key is present: an existing message with the same key is returned after a **payload check** (same receiver type, receiver id and content) and a mismatch is a `409`; no second row, no second thread increment, no second `NewMessageEvent`.
- The transaction moved from `@Transactional` to a `TransactionTemplate`, because a concurrent duplicate is rejected by the unique index at flush time: that kills the first transaction, so the duplicate is resolved by re-reading in a **fresh** transaction rather than swallowing the violation inside a poisoned one.
- Schema v5: adding a column is exactly what `CREATE TABLE IF NOT EXISTS` cannot do, so the guard's upgrade step now carries its own DDL (`ALTER TABLE … ADD COLUMN`, `CREATE UNIQUE INDEX`) and runs it against the connection's own schema; the chain must be contiguous from the recorded version (no silent jumps), and the version is recorded only after the column and index are verified — including a generated H2 index name, matched by prefix.
- UI (both views): one `clientMessageId` per send attempt (`crypto.randomUUID()` with a fallback), the optimistic bubble carries `sending`/`failed` status, a failed bubble keeps the text with a **retry** action that re-sends the same key, and a response is applied to the panel **only if it belongs to the partner it was addressed to** — a late answer cannot land in the thread the user switched to.

**Red evidence (actually run).**

First run, before any production change (`mvn test -Dtest=MessageIdempotencyIntegrationTest`) — the key has no column yet:

```
Tests run: 6, Failures: 0, Errors: 5
Caused by: org.h2.jdbc.JdbcSQLSyntaxErrorException: Column "CLIENT_MESSAGE_ID" not found
```

Second run, after the schema and plumbing but **before** the deduplication logic — this is the behavioural red:

```
repeatedSendWithTheSameKey_shouldReturnTheSameMessageStoredOnce:103  Status expected:<200> but was:<500>
sameKeyWithDifferentContent_shouldConflict:124                       Status expected:<409> but was:<500>
sameKeyWithDifferentReceiver_shouldConflict:140                      Status expected:<409> but was:<500>
concurrentRepeatsOfOneKey_shouldEndUpAsOneMessage:208                » Execution … expected:<200> but was:<500>
```

The 500s are the unique index rejecting the second insert with nothing handling it — exactly the failure the round exists to remove.

**Tests:** `MessageIdempotencyIntegrationTest` (6): a repeat returns the same id and stores one row, one thread entry and one event; the same key with different content and with a different receiver are both `409` and store nothing; the same key from a patient and a doctor are two messages; keyless sends are never deduplicated; eight concurrent repeats of one key all return the same id and end as one row. `DevSchemaGuardTest` gained `appliesTheColumnStepItself` (a minimal v4 database is upgraded by the step's DDL, version recorded, and the new index really rejects a duplicate key); `SchemaUpgradeIntegrationTest` gained an assertion block proving a v3 database booted by this build ends with the column, keeps the planted messages on a null key, and enforces the key.

**Verification (actually run, first pass):** `mvn clean verify` → **298 tests, 0 failures, 0 errors, BUILD SUCCESS** (290 before this round + 8); `LayeringGuardTest` 4/4 with no allowlist. The three changed classes pass under JUnit's random orderer with seeds 6 and 13 (`Tests run: 17, Failures: 0` each). Frontend: `npm run check` → 0 errors (4 pre-existing warnings, none in the code added here; the one in `views/chat/index.tsx` is the untouched URL-partner effect) and `npm run build` → built (433 kB JS / 8.75 kB CSS). The review pass below raises the backend count to 299 and adds the frontend unit tests.

**Limits of this slice (not claimed):**
- The UI behaviour (retry keeps the text, no duplication, no cross-thread landing) is covered by pure-function unit tests added in the review pass (`src/utils/chatState.test.ts`, Vitest) — see the 54.3 review record below. Component rendering and SSE wiring remain covered by type check, lint, build and review only.
- A failed message survives only while its conversation stays open: switching threads reloads the panel and the failed bubble is gone. The text is preserved in the bubble, not in a durable outbox — a per-conversation pending store would be a separate slice.
- `clientMessageId` is optional: an API client that omits it keeps the pre-54.3 non-idempotent behaviour, by design.
- The upgrade DDL is proven on H2 (MySQL mode) only; MySQL still applies the column by hand (`README.md`).
- The audit of a duplicated send still records the call (it happened); the duplicate itself creates no message, thread revision or notification.

**Files:** `sql/schema.sql`, `common/config/DevSchemaGuard.java`, `module/chat/entity/Message.java`, `dto/MessageFormDTO.java`, `dto/MessageVO.java`, `repository/MessageRepository.java`, `service/ChatService.java`, both chat controllers, `medical-web/src/{api/chat.ts,types/entities.ts,utils/chat.ts,views/chat/index.tsx,views/chat/style.module.css,views/patient/chat/index.tsx}`, the three test classes, `docs/API-LAYOUT.md`, `README.md`.

### Round 54.3 review fixes (second pass, 2026-10-21)

Four findings from the review of `d0dca63` were re-checked; all four held, and the test evidence the review called thin was tightened.

**1. P2 — a retry stacked bubbles.** `handleRetry` flipped the failed bubble to `sending` and then called `sendAttempt`, which unconditionally appended a new placeholder: the first retry showed two bubbles, the next three. The send state now lives in pure helpers (`src/utils/chatState.ts`): `withAttempt` finds the bubble the attempt already owns and only flips its status, so first send and retry are the same code path.

**2. P2 — a page-one history response wiped a failed message.** `setMessages(batch)` replaced the panel with the snapshot, so a GET that started before the send deleted a bubble created after it — no conversation switch needed. Page one is now merged: `mergeHistory` keeps local attempts the snapshot cannot contain, dedupes by message id **and** by attempt identity, and a late snapshot cannot drop a message this client already saw acknowledged (`confirmedIds` is cleared when a snapshot carries the id). A successful send also refreshes page one, and `loadSeqRef` discards the older in-flight GET.

**3. P2 — the UI matched a bare clientMessageId.** The backend scopes the key by the sender's typed identity — a patient and a staff account may legitimately reuse one string — but `settle`/`fail` matched on the key alone, so the other side's message could be removed or marked failed. Matching is now `senderType:senderId:clientMessageId`, and failure marking only touches bubbles this client created (an attempt has a local status; a stored message does not).

**4. P2 — an interrupted upgrade could not be finished.** The step re-ran its whole DDL group whenever the column *and* index were not both present, so a crash between `ADD COLUMN` and `CREATE UNIQUE INDEX` left a database that could never start again (the next `ADD COLUMN` fails as a duplicate). Column and index are now checked and added **separately**, and the version is recorded only after both verify. `finishesAPartiallyAppliedStep` builds exactly that state (version 4, column present, index absent, a message row already there) and proves the guard finishes it and keeps the row; against the previous code it fails with `Duplicate column name "CLIENT_MESSAGE_ID"` — recorded, then the fix restored.

**Test evidence tightened (review note):** `MessageIdempotencyIntegrationTest` counted `message` rows but not the thread, so the claim "the thread's count and revision move once" was untested. Both the repeat test and the eight-sender concurrency test now compare `chat_conversation.message_count` and `revision` before and after and require exactly `+1`.

**Frontend tests.** The UI rules were moved into pure functions and are now executed: Vitest (`npm test`, a dev-only dependency; the bundle is unchanged) covers one bubble across repeated failures, replacement by the stored message exactly once, a response that belongs to another conversation, the same key used by both parties (both messages kept, only the local one is marked or replaced), a failed bubble surviving a later page-one response, no duplication when the snapshot carries the message, a stale snapshot not dropping a just-sent one, and forgetting a confirmed id once it is in a snapshot — **10 tests**. These are new tests for logic that did not exist as a testable unit before, so they have no red state of their own; what they pin down is proven by the four findings above.

**Verification (actually run):** `mvn clean verify` → **299 tests, 0 failures, 0 errors, BUILD SUCCESS** (298 + the new guard test); `LayeringGuardTest` 4/4 with no allowlist. `npm test` → 10/10. `npm run check` → 0 errors (the same 4 pre-existing warnings, none in the new code). `npm run build` → built (433.87 kB JS / 8.75 kB CSS).

**Still not claimed:** Vitest runs the state helpers, not the React components — rendering, SSE wiring and the composer remain covered by type check, lint, build and review. The `clientMessageId` is still optional, MySQL still receives the column by hand, and the failed bubble is still lost when its conversation is closed (a persisted outbox is a separate slice).

### Round 54.4 — After-commit notification and safety notice (Done, 2026-10-21)

**Source baseline:** `b0cb4955` (54.3 complete after review), clean tree. **Recorded test baseline (this round):** `mvn clean verify` → **299 tests, 0 failures**.

**What the source had:** `ChatService` published `NewMessageEvent` *inside* the send transaction, so anything that failed after that point (a thread write, a constraint at commit) still ran the async listener and pushed `new_message` to the receiver for a message that no longer existed. Nothing asserted what a *failing* notification does to an already-committed message, because the event and the commit were the same step. And there was no safety notice anywhere: neither portal said what to do in an emergency or that the channel is not a real-time service.

**Design.**
- `notifyAfterCommit(event)` registers a `TransactionSynchronization` and publishes in `afterCommit`; `publishQuietly` logs a failing listener instead of propagating it, so a committed message is never reported as a failed send. The async SSE listener is unchanged: the ordering guarantee is about the transaction, not the thread.
- `SafetyNoticeService` is the deterministic entry point: fixed, reviewed wording plus `NOTICE_VERSION`, served as `SafetyNoticeVO { version, emergencyNotice, responseNotice }` from both chat controllers. Nothing in that path consults a model, a retriever or any external service, and the notice deliberately states the boundary ("not monitored around the clock", "may take time") rather than implying triage.
- Both chat views fetch the notice once and render it under the conversation header.

**Red evidence (before the production change, `mvn test -Dtest=ChatCommitEventIntegrationTest`):**

```
Tests run: 5, Failures: 3
theNotificationIsPublishedOnlyAfterTheCommit … expected: <1> but was: <0>   (the probe could not see the message from another connection: it was delivered inside the transaction)
aFailingNotification_shouldNotUndoTheStoredMessage … expected:<200> but was:<500>
theSafetyNoticeIsFixedVersionedAndServedToBothPortals … expected:<200> but was:<400>  (no endpoint)
```

A second, stronger probe was tried first: asserting `TransactionSynchronizationManager.isActualTransactionActive()` inside the listener. It stays true during `afterCommit`, so it measured the wrong thing; the visibility check from a second connection is what actually distinguishes "published inside" from "published after commit", and it is the check that is committed.

**Tests (5):** a send whose thread write throws rolls back (no row) and publishes nothing; the event is only delivered once the message is visible on another connection; a listener that throws does not undo the message and still gets called; both portals receive the same versioned notice with the emergency and no-real-time wording; the notice requires an authenticated identity on both routes.

**Verification (actually run):** `mvn clean verify` → **304 tests, 0 failures, 0 errors, BUILD SUCCESS** (299 + 5); `LayeringGuardTest` 4/4 with no allowlist. Frontend: `npm run check` → 0 errors (the same 4 pre-existing warnings), `npm test` → 10/10, `npm run build` → built (434.47 kB JS / 8.93 kB CSS).

**Limits of this slice (not claimed):** the notification is still an in-process async listener on `auditExecutor` — durable outbox rows, leases, retries and database reconciliation are 55.2, and SSE remains a refresh hint rather than a delivery guarantee. The notice is English, synthetic-clinic wording that has not been clinically reviewed; it is not triage, and the absence of a safety rule hit is not evidence that a message is safe. "Independent of model availability" is currently true by construction (no AI component exists); rounds 55–57 must keep it true and the tests here are the baseline for that. MySQL parity for the notice (no schema change) is unaffected.

### Round 54.3 / 54.4 review fixes (third pass, 2026-10-21)

Two findings from the review of `6f5fa08d` were re-checked; both held, and the reviewer's non-blocking test suggestion was implemented.

**1. P2 — a send skipped a page (54.3).** `settle()` refreshed page one after a successful send but left the panel counter at whatever page was open: with page two loaded, the refresh replaced it with page one, and "load earlier" then asked for page **three**, so page two could never be seen again. The panel is now one value — `{messages, page, total}` — updated only through `panelAfterPage`, which records the page the panel actually holds, and `nextPage` derives the button's request from it. The views no longer own a separate counter, so content and counter cannot drift. Tests in `chatState.test.ts`: page two → page-one refresh → `nextPage` is 2 and only the refreshed page remains; prepending an older page keeps its page; a failed attempt still merges into the refresh.

**2. P2 — a failed notice request removed the notice entirely (54.4).** Both views started at `null`, set `null` again on failure and requested once, so a timeout took the emergency text off screen while the composer kept working. `FALLBACK_SAFETY_NOTICE` (version `0`, fixed in the client) is now the initial state and what remains if the request fails; `displayedSafetyNotice` picks the server's reviewed wording once it arrives, and the banner renders unconditionally when a conversation is open. Tests in `safetyNotice.test.ts` cover the three display states — in flight, rejected, resolved.

**3. Reviewer's non-blocking suggestion, implemented.** `aRollbackOnlySend_shouldNotRunTheRegisteredCallback` runs the real thread write, then marks the transaction rollback-only, after which the send registers its after-commit callback; the commit then finds a rollback-only transaction, and the test asserts that nothing is stored and nothing is published — the "callback registered, commit never happened" case the earlier rollback test stopped short of. (The order is thread write → rollback-only → registration → rollback; an earlier wording of this record said the marking came after the registration, which was wrong. Round 54 review.)

**Verification (actually run):** `mvn clean verify` → **305 tests, 0 failures, 0 errors, BUILD SUCCESS** (304 + 1); `LayeringGuardTest` 4/4 with no allowlist. Frontend: `npm test` → **16/16** (13 chat-state + 3 notice), `npm run check` → 0 errors (the same 4 pre-existing warnings), `npm run build` → built (435.19 kB JS / 8.93 kB CSS).

**Still not claimed:** the paging and notice tests exercise the state helpers, not the rendered components — that boundary is unchanged. Round 54 is complete on this evidence, but acceptance remains the reviewer's call.

## Round 55 — Python service and reliable execution (Done: 55.1–55.4, H2 + SQLite)

**Goal:** complete the Mock Agent transport and recovery path before real-model behavior. **Estimate:** 32–48 hours. Depends on Round 54.

| Slice | Priority / status | Work | Tests first / acceptance |
|---|---|---|---|
| 55.1 Service and contract skeleton | P0 / **Done** (2026-10-21, after three review rounds) | FastAPI, configuration, Pydantic (strict identity fields, timezone-aware ISO timestamps with a validated offset), Provider interface/MockProvider, health checks, basic Compose, cross-language fixtures | test_contracts.py, test_config.py: bad payloads denied on both sides (24 fixtures incl. null, bounds, code-point lengths, coercions, timestamp spellings and offset ranges, nested types), missing required credentials fail, credentials never appear in startup errors, Java/Python agree — 58 pytest + 8 Java contract tests, ruff and mypy --strict clean (see the 55.1 records) |
| 55.2 Durable dispatcher | P0 / **Done** (2026-10-22, after review) | agent_run atomic intake; claim/lease/attempt/deadline (all enforced in the claim); attempted-error categories instead of remote text; bounded backoff; separate executor; reconcile expired and exhausted claims | AgentDispatchIntegrationTest + AgentRestartRecoveryIntegrationTest: Python down does not break message send; a refused/rejected dispatch is retried without storing the response body; a restart dispatches the queued job from a second application context; expired deadlines and exhausted attempts never reach the service; one claim winner per job — 16 tests (see the 55.2 records) |
| 55.3 Service/task authorization | P0 / **Done** (2026-10-22, after review) | Directional outbound/inbound credentials, opaque capability digests bound to run + generation, generation- and state-fenced dispatch writes, live-lease/deadline checks inside verify **and** the result UPDATE, current account check, internal allowlist | AgentExecutionAuthorizationTest + AgentDispatchIntegrationTest: forged/expired/cross-run/revoked/stale access rejected, a result accepted mid-dispatch is never reopened or requeued, the outbound token is not an inbound one, expired leases and deadlines refuse GET and POST, and the result UPDATE itself refuses a write whose lease lapsed after the check — 35 agent tests (see the 55.3 records) |
| 55.4 Minimal persistent graph | P0 / **Done** (2026-10-22, H2 + SQLite; Postgres deferred by the 2026-10-22 scope decision) | Intake → validation → Mock classification → draft → result submission; LangGraph checkpoints (SQLite in this environment); recovery by resume; run-level serialization | AgentEndToEndIntegrationTest (real Java + uvicorn processes): a refused callback requeues the run, the retry claims generation 2 and the second Python process resumes the checkpoint, submitting generation 2 → WAITING_REVIEW; messaging stays available while the agent fails — plus the 79 Python tests (see the 55.4 records) |

Run the checks locally: backend regression (`mvn clean verify`), Python lint/type/Mock tests
(`ruff`, `mypy`, `pytest`) and the frontend checks (`npm run check`, `npm test`, `npm run build`),
plus the opt-in end-to-end test (`mvn -Dtest=AgentEndToEndIntegrationTest -Dagent.e2e=true test`).
There is **no CI workflow**: one was written during Round 55.4 and removed by the owner in
`fb8b4134` ("use local validation"), so do not reintroduce it as part of these rounds (2026-10-22
decision). Distinguish liveness from readiness and keep provider failure from making human
messaging unavailable.

### Round 55.1 — Service and contract skeleton (Done, 2026-10-21)

**Baseline:** `fea06c68` (Round 54 accepted), clean tree, backend **305 tests**. Toolchain verified first: `python3` on this machine is 3.9, `python3.12` is 3.12.7, no `uv`/`poetry`, PyPI reachable, Docker CLI present but **no daemon running**.

**What was built** (`medical-agent/`, Python 3.12):

| Piece | What it does |
|---|---|
| `pyproject.toml` + `requirements.lock` | Project metadata, dependency ranges, tool config (pytest/ruff/mypy) and the pinned environment (31 packages) the tests and the image share |
| `src/medical_agent/config.py` | `Settings` from `MEDICAL_AGENT_*` env: inbound token **required**, DeepSeek key required when `provider=deepseek`, positive timeout; secrets are `SecretStr`, so a token cannot leak through a log line |
| `src/medical_agent/schemas/run.py` | The versioned wire contract: `RunStartRequest`, `RunStatusResponse`, `RunError`, `ErrorResponse`, `RunState` (7 states) — `extra="forbid"` and `contract_version` pinned to `v1` |
| `src/medical_agent/contracts.py` + `scripts/export_contracts.py` | The committed JSON Schemas under `contracts/agent/v1/` are **generated from the models**; a test fails when they drift |
| `contracts/agent/v1/fixtures/` | Six cross-language fixtures: three valid, three deliberately invalid (missing required, unknown field, bad enum) |
| `src/medical_agent/providers/` | `Provider` protocol, `ProviderRequest/Response`, deterministic `MockProvider`, and a factory that **refuses** `deepseek` with a clear message until 56.1 instead of silently falling back to the mock |
| `src/medical_agent/api/app.py` | `GET /health/live` (process only) and `GET /health/ready` (provider + contract version, `503` when the configured provider cannot be built) |
| `Dockerfile`, `compose.yaml` | A single-service Compose definition with a required inbound token and a liveness healthcheck; MySQL/PostgreSQL join in the rounds that use them |

**Tests (32 pytest + 4 Java).** `test_config.py` (10): missing token fails, DeepSeek without a key fails, DeepSeek with a key is valid, unknown provider fails, non-positive timeout fails, the token is not echoed. `test_contracts.py` (8 functions / 12 cases): committed schemas equal the generated ones, every schema forbids unknown fields, valid fixtures parse, invalid fixtures are refused, the status fixture uses a contract state, the start fixture carries no model-supplied identity. `test_health.py` (3): liveness ignores the provider, readiness reports provider + contract, readiness is `503` for a provider this round cannot build. `test_provider.py` (7): the mock is deterministic and free, request bounds, factory selection and the DeepSeek refusal. Java `AgentContractFixtureTest` (4): reads the same schemas and fixtures, requires `additionalProperties: false` and a `required` set, accepts the valid ones, rejects the invalid ones.

**Verification (actually run):** `.venv/bin/python -m pytest` → **32 passed**; `ruff check .` → clean; `mypy` (strict, `files = ["src"]`) → "no issues found in 12 source files"; `mvn clean verify` → **309 tests, 0 failures, BUILD SUCCESS** (305 + 4), `LayeringGuardTest` 4/4; `MEDICAL_AGENT_INBOUND_TOKEN=… uvicorn … :8099` → `/health/live` **200** and `/health/ready` **200 `{provider: mock, contract_version: v1}`**; `docker compose config --quiet` → valid. The Java fixture check was proved non-vacuous by removing `run_id` from a valid fixture (failure: `missing required field 'run_id'`) and restoring it.

**Limits of this slice (not claimed):** no run endpoints yet — the durable dispatcher is 55.2, and shipping a stub that accepts work it cannot do was rejected; no DeepSeek adapter (56.1) and no LangGraph/PostgreSQL (55.4+); **the Docker image was not built** because this machine has no Docker daemon, so only the Compose file's syntax is verified; the Java side has no agent DTOs yet — only the fixture-agreement test, and the dispatcher's DTOs arrive with 55.2; no CI workflow (the round's closing instruction was later dropped; validation is local by the 2026-10-22 decision); the Python tests run against a venv created with `python3.12` explicitly, because the default `python3` here is 3.9.

### Round 55.1 review fixes (second pass, 2026-10-21)

Three findings from the review of `4ab359c2` were re-checked; all three held, and each is now covered by a fixture or a test that failed before the fix.

**1. P2 — the Java contract check accepted payloads Python refuses.** The hand-written validator skipped `null` and ignored `minimum`/`exclusiveMinimum`, string lengths, `anyOf` and `date-time`. Measured against the new boundary fixtures, the **old** validator accepted five of them: `empty content` (length), `run_id: null` (null skipped), `run_id: 0` (bound), `error: 123` (nested `$ref` through `anyOf`), and a non-date `updated_at` (format). It now implements exactly the keywords these contracts use — `$ref`, `anyOf`, type, required, `additionalProperties`, `enum`, `const`, numeric bounds, string lengths and `format: date-time` — and, because a hand-rolled validator can fall behind, two guard tests fail if a schema starts using a keyword or format it does not check. A mature JSON Schema library (networknt) was the preferred fix and was written first, but this sandbox cannot write the Maven repository, so the build could not be verified with it; the dependency was reverted and the risk bounded by the keyword guards instead. That trade-off is recorded here rather than hidden.

**2. P2 — Pydantic coerced the identity fields.** `"run_id": "123"` parsed as `123` and `"run_id": true` as `1`, so Python accepted payloads Java refuses. Identity and revision fields are now strict (`Field(strict=True)` with the same bounds), verified through the real JSON entry point (`model_validate_json`), and two fixtures (`invalid-numeric-string-run-id`, `invalid-boolean-run-id`) pin the rule from both sides. Strictness does not change the generated schemas, so the committed files are unchanged.

**3. P1 — credentials could appear in a validation error.** `SecretStr` only masks a successfully built object; a model-level failure renders the input, and a **short** token appeared verbatim in `str(exc)` (a long one was merely truncated by luck). `hide_input_in_errors=True` fixes the message text, but Pydantic keeps the raw input in `exc.errors()` regardless, so the startup path no longer lets that structure reach a logger: `load_settings()` converts it to `ConfigurationError`, built from field paths and messages only, raised `from None`. Tests assert that a synthetic inbound token and API key appear in neither the Pydantic message nor the startup error, that the startup module (`medical_agent.api.main`, what uvicorn imports) fails with the sanitized error, and that the error still names the offending setting.

**Fixtures added (shared by both runtimes, 13 total):** null identity, zero identity, empty content, numeric-string identity, boolean identity, nested `error` of the wrong type, non-date timestamp.

**Verification (actually run):** before the fixes — the old Java validator's accepted set (five fixtures, listed above), Python accepting `"123"`/`true`, and a short secret visible in `str(exc)`. After: `pytest` → **44 passed**; `ruff check .` → clean; `mypy` (strict) → no issues in 12 source files; `mvn clean verify` → **313 tests, 0 failures, BUILD SUCCESS** (309 + 4 contract tests); `LayeringGuardTest` 4/4 with no allowlist.

**Still not claimed:** the Java validator implements the keywords the contracts use *today* and refuses to run silently if that changes, but it is not a general JSON Schema implementation; the dependency alternative remains the better long-term choice once the build environment can fetch it. Docker image, run endpoints and CI remain as recorded in the first 55.1 pass.

### Round 55.1 review fixes (third pass, 2026-10-21)

Two remaining cross-language divergences were re-checked; both held, and both now have fixtures that failed before the fix.

**1. P2 — Python accepted timestamps Java refuses.** Measured with `model_validate_json` on the committed model: an epoch number `0` became 1970-01-01, `"2026-10-21"` became midnight, and a naive `"2026-10-21T12:00:00"` was accepted — Java's `OffsetDateTime.parse` refuses all three. `updated_at` is now `WireTimestamp`: an `AwareDatetime` behind an explicit parser that requires an ISO-8601 string with `T` and an offset (or `Z`/`z`), refuses numbers, date-only strings, naive date-times and the space-separated spelling, and still accepts a `datetime` instance so the service can build its own responses. Two things were learned while doing it and are worth recording: `AwareDatetime` alone still accepted the epoch number, and combining `strict=True` with a `BeforeValidator` made Pydantic reject *every* string (the strict datetime schema no longer treats the value as JSON-native), so the parser itself does the checking. Fixtures added: valid `Z` and `+02:00` (a second valid sample with an explicit offset), invalid epoch, date-only, naive and space-separated. Lowercase `z` is accepted on both sides — Java's parser takes it, so Python matches rather than diverging.

**2. P2 — Java counted UTF-16 units, Python counts code points.** A content of 2001 😀 is 2001 code points and 4002 UTF-16 units: Python accepted it, Java rejected it as "longer than 4000 characters". The validator now uses `codePointCount` for `minLength`/`maxLength`, which is also what JSON Schema specifies. Fixtures added: `run-start.valid.supplementary-characters.json` (2001 emoji, accepted by both) and `run-start.invalid-too-long-content.json` (4001 emoji, refused by both).

**On the reviewer's caveat.** The keyword guard only notices a schema using a keyword the validator does not implement; it cannot notice a semantic difference such as the length counting above. That is why the second and third review rounds both produced fixtures rather than only guards, and why this record does not claim a JSON Schema implementation: the validator covers the keywords and the semantics the contract uses **today**, the fixtures are the evidence that both sides agree on them, and a mature validator remains the right long-term replacement once the build environment can fetch one.

**Verification (actually run).** Before: the Python timestamp probe accepted `0`, `"2026-10-21"` and `"2026-10-21T12:00:00"`; the Java side failed the new emoji fixture with `$.content: longer than 4000 characters`. After: `pytest` → **54 passed** (20 fixtures, 0 mismatches in a direct model-vs-fixture sweep); `ruff check .` → clean; `mypy` (strict) → no issues in 12 source files; `mvn clean verify` → **313 tests, 0 failures, BUILD SUCCESS**; `LayeringGuardTest` 4/4. The generated schemas did not change (`datetime` still renders as `string`/`date-time`), so the committed files and their drift test are untouched.

### Round 55.1 review fixes (fourth pass, 2026-10-21)

**P2 — the timestamp parser did not validate the offset range.** The pattern accepted any two digits for the offset hours and minutes, and `datetime.fromisoformat()` then *normalised* an impossible one rather than refusing it: `+02:99` became `+03:39`. Java's `OffsetDateTime.parse` refuses `+02:99`, `+19:00`, `+18:01` and `-19:00`, and also refuses the colon-less `+0200`, so Python accepted values that meant something different on each side.

Fixed by validating the offset the way Java does: minutes must be 00–59, hours at most 18, and 18 only as `18:00`; the colon in `±HH:MM` is now required, because Java rejects `+0200`. The groups are captured while matching so the check happens before any parsing.

**Red evidence (probed before the fix):** Python accepted `+02:99` (as `+03:39`), `+19:00`, `+18:01`, `-19:00` and `+0200`; Java refused all five, accepted `±18:00` and refused `+18:01`. After the fix the two runtimes agree on that whole matrix, including `±18:00` as the boundary.

**Fixtures added (24 total):** `valid.offset-boundary-timestamp` (`+18:00`), `invalid-offset-minutes-timestamp` (`+02:99`), `invalid-offset-too-large-timestamp` (`+19:00`), `invalid-offset-no-colon-timestamp` (`+0200`). The Python contract test now walks the accepted/rejected matrix explicitly, and a direct sweep of all 24 fixtures against the models reports **0 mismatches**.

**Verification (actually run):** `pytest` → **58 passed**; `ruff check .` → clean; `mypy` (strict) → no issues in 12 source files; `mvn clean verify` → **313 tests, 0 failures, BUILD SUCCESS**; the generated schemas are unchanged.

**Still not claimed:** the offset range is now checked, but the contract remains "the spellings both runtimes parse" rather than a full ISO-8601 profile — forms neither side produces (`+02`, `+02:00:30`) are not accepted and not claimed. The validator is still fixture-backed, not a JSON Schema implementation.

### Round 55.2 — Durable dispatcher (Done, 2026-10-22)

**Baseline:** `ef975b6e`, clean tree, **313 tests**. Schema v6.

**What was built** (all in `module/chat`, the module the plan assigns message jobs to):

| Piece | What it does |
|---|---|
| `agent_run` table | One row per patient message, written **in the message's own transaction**, with a unique key on `message_id`; state, attempt, max attempts, `next_attempt_at`, `deadline_at`, lease owner/expiry and `execution_generation` |
| `AgentRunService` | Intake (idempotent on the message id), a dispatch pass over due rows, the reconcile sweep, and the backoff/attempt policy |
| `AgentRunStateStore` | Short transactions for every state change, so the claim is **committed before** the HTTP call and a slow model never holds a database transaction open |
| `AgentRunRepository` | Guarded UPDATEs: `claim` (one winner, increments attempt and generation), `markState`, `retryLater`, `fail`, `reclaimExpired`, `expireDeadlines` |
| `HttpAgentRunClient` | `POST /internal/v1/runs` with the v1 contract body and the configured service token, bounded connect/read timeouts |
| `AgentRunStartRequest` | The Java side of the contract, field for field; the content is cut to the contract's 4000 code points and `request_id` is derived from run + generation so a retry is recognisable |
| `AgentRunScheduler` + `AgentDispatchConfig` | A **dedicated** `ThreadPoolTaskScheduler` selected by name, so a slow model cannot delay the audit/quality schedulers |
| `app.agent.*` config | Off by default; base URL, service token, poll interval, batch size, lease, deadline, attempts, backoff, request timeout |

**Tests (`AgentDispatchIntegrationTest`, 10).** A local JDK `HttpServer` stub stands in for the agent service and records the bodies Java sends, so the test observes the real contract and can fail it on demand. Asserted: a stored patient message creates exactly one PENDING run with the conversation revision; staff messages create none; a dispatch posts the v1 contract (`contract_version`, `run_id`, `message_id`, `patient_id`, `sender_type`, `content`, `request_id`) and marks the run RUNNING with attempt 1 and generation 1; a 503 leaves the message stored, the run PENDING with attempt 1, an error and a retry in the future; two concurrent dispatch passes make **one** call; a failed run is replayed once the service recovers (same row, attempt 2); an expired lease is reclaimed and re-dispatched; a run past its deadline ends FAILED without calling out; exhausting attempts ends FAILED; and a refused connection surfaces as an exception the queue turns into a retry.

**Red evidence.** The first run of the new class failed with `TransactionRequiredException: No EntityManager with actual transaction available` on every state change — the `@Modifying` queries had no transaction because the dispatch pass deliberately does not wrap the HTTP call. That produced the `AgentRunStateStore` split (claim/ack/retry/fail each in its own short transaction), which is the design that survived. The full gate then caught the guard tests still assuming v5 semantics (`SCHEMA_VERSION - 1`/`- 2` now point at different steps); they were rewritten to name the version they are about (3 → the v4 table step, 4 → the v5 column step) and their minimal probe databases now include the later tables the chain requires.

**Verification (actually run):** `mvn clean verify` → **323 tests, 0 failures, 0 errors, BUILD SUCCESS** (313 + 10); `LayeringGuardTest` 4/4 with no allowlist. Dispatch configuration defaults to `enabled=false`, so the whole existing suite (and every deployment that has not opted in) sends no agent traffic.

**Limits of this slice (not claimed):**
- **The receiving endpoint does not exist yet.** Python implements no run endpoints, so against a real agent service the dispatcher gets a 404 and backs off — the stub proves the Java behaviour, not an end-to-end run. The intake/authorization endpoints belong to 55.3/55.4.
- No result submission, review or publication (Rounds 55.3/57); a RUNNING run stays RUNNING until a result arrives, and after its lease expires the sweep puts it back — the generation exists but is not yet used to fence a stale worker (55.3).
- The claim is a guarded UPDATE, which is correct for the single-instance deployment the plan specifies; multi-instance lease safety beyond that is not claimed.
- No CI workflow (that instruction was dropped on 2026-10-22; validation is local), and the Docker image is still unverified (no daemon here).
- Intake runs only for `PATIENT` senders; it adds one revision lookup per such send and creates no rows while the feature is off.

### Round 55.2 review fixes (second pass, 2026-10-22)

Four findings were re-checked against the source; all four held.

**1. P1 — the remote error body could reach the database and the logs.** `deliver()` stored `failure.getMessage()` (truncated to 500) in `last_error` and logged it; a `RestTemplate` error message includes the response body, which for a validation failure can quote the rejected patient text. The HTTP client now converts transport failures into `AgentDispatchException` carrying **only a category** — `AGENT_HTTP_422`, `AGENT_UNREACHABLE`, `AGENT_CLIENT_ERROR` — and the service stores and logs that category, never a message. Test: the stub answers 422 with a synthetic marker in the body; the row's `last_error` is exactly `AGENT_HTTP_422`, the marker appears in neither the row nor the captured Logback events, and the job stays PENDING for a retry.

**2. P2 — a job past its deadline was still dispatched.** `dispatchDue()` selected and claimed without looking at `deadline_at`, and the scheduler ran dispatch **before** reconcile, so an expired job was sent and only afterwards failed by the sweep. The claim now enforces `deadline_at > now`, the due query filters the same way, and `runOnce()` reconciles **before** it dispatches (the pass is a public method so tests drive the real order). Tests: an expired job passes through `runOnce()` with **zero** HTTP calls, attempt unchanged and state FAILED; and a second job that expires while the first request is in flight is refused by the claim (`PENDING`, attempt 0) and then failed by the sweep.

**3. P2 — the attempt limit did not bound claiming.** `claim` ignored `attempt < max_attempts`, so an exhausted job was sent one more time and only failed afterwards; and a RUNNING job whose lease expired on its last attempt was skipped by `reclaimExpired` and sat there until the overall deadline. The claim now enforces the attempt bound, and reconcile ends both cases explicitly (`failExhaustedPending`, `failExhaustedRunning`). Tests: an exhausted PENDING job makes zero calls and ends FAILED with its attempt counter unchanged; a RUNNING job whose last attempt's lease expired becomes FAILED immediately.

**4. P2 — one `now` for the whole batch.** Leases were computed from the batch's start time, so a job claimed after a slow earlier request could already be expired — and the next reconcile would reclaim a job that had just been dispatched successfully. Each claim now takes the clock again and computes its own lease; the deadline check uses that same instant. Test: with a 1 s lease, the first request delayed 2.5 s and the second answered immediately, the second job's `lease_expires_at` is in the future after the batch and `reconcile()` leaves it RUNNING. The same test also records the other side of that trade-off: a dispatch slower than its own lease **is** reclaimed, which is why the lease must exceed the client timeout.

**Evidence tightened as requested.**
- The old "restart" test only re-ran the dispatcher in the same context. `AgentRestartRecoveryIntegrationTest` now boots the application twice against one in-memory database: the first context queues a job and closes, the second — a genuinely new application instance — dispatches it, and the assertions read the row through a fresh connection after both contexts are gone. The renamed `aFailedRunIsReplayedAfterItsBackoff` claims only what it proves: a backoff retry inside one process.
- The two-thread test is described as what it is: **one claim winner per job**. End-to-end idempotency at the receiving service, and result handling, are not implemented and are not claimed.
- The guard is precise now: **`claim` is a guarded UPDATE**; `markState`, `transition` and `retryLater` are conditioned on the id alone. Generation fencing for those writes arrives with 55.3 and is not claimed here.

**Verification (actually run):** `mvn clean verify` → **329 tests, 0 failures, 0 errors, BUILD SUCCESS** (323 + 5 dispatcher tests + 1 restart test); `LayeringGuardTest` 4/4. The red state for the review items was observed before the fixes: the 422 body was stored verbatim, an expired job was sent, an exhausted job made one more call, and the second job of a slow batch was reclaimed immediately.

**Still not claimed:** no Python run endpoint exists, so a real dispatch answers 404 and backs off; no result submission, review or publication (55.3/57); multi-instance lease safety beyond the guarded claim; no CI workflow (validation is local, 2026-10-22); the Docker image is unverified here.

### Round 55.3 — Service and task authorization (Done, 2026-10-22)

**Baseline:** `125132a6`, clean tree, **329 tests**. Schema v7.

**Two credentials, two meanings.** The **service token** (`app.agent.service-token`, sent as
`X-Agent-Service-Token`) says which process is calling; a blank configuration denies every
internal call rather than opening them. The **capability** is a 32-byte random token issued
per attempt, sent as `X-Agent-Capability`; the database stores only its SHA-256 digest, bound
to the run and to the `execution_generation` that issued it. Neither credential alone reaches
patient data.

**What enforces it.**

| Piece | Guard |
|---|---|
| `AgentServiceToken` | Constant-time comparison; blank configured token → every call denied |
| `AgentCapabilityService.issue` | Random token, digest-only storage, per-run + per-generation row, TTL (`app.agent.capability-ttl`, 5 min) |
| `AgentCapabilityService.verify` | Known digest, same run, not expired, generation equals the run's current one, run state `RUNNING` |
| `AgentCapabilityService.revoke` | Capabilities are deleted when a run stops accepting work |
| `AgentInternalController` | The whole internal allowlist: `GET /runs/{runId}` and `POST /runs/{runId}/results`; both re-check the patient's current account state |
| `AgentRunRepository` | `markState`, `transition`, `retryLater` and the new `complete` are now guarded by `execution_generation`, so a worker whose lease was reclaimed cannot write over the attempt that replaced it |
| `SecurityConfig` | `/api/v1/internal/agent/**` is `permitAll` so the controller can authenticate its own callers; the patient API still needs a JWT |

**Tests (`AgentExecutionAuthorizationTest`, 13).** A missing service token, a wrong one, a
missing capability and a forged one are refused; a valid pair reads the run context; the
service token alone cannot read `/api/v1/patients/100` (the internal allowlist is not a
patient credential); a capability cannot be used for another run; an expired capability is
refused; a capability from a previous generation is refused; a successful result moves the
run to `WAITING_REVIEW` and revokes the capability, after which the same token is refused; a
result carrying a stale generation is a conflict and leaves the run untouched; a patient
whose account is disabled stops the worker; and the database holds a 64-hex digest — the raw
token appears in no row.

**Red evidence.** The capability table did not exist, so `verify` could only answer "unknown
capability": the forged-capability test passed for that reason until the issue path existed,
and the generation test could not be expressed at all. The full gate then caught the schema
guard's minimal probe databases, which started at v4 and needed the new v7 table to complete
the chain (`The step to v7 needs table 'agent_capability'`), the same way 55.2 had extended
them for `agent_run`.

**Also fixed from the 55.2 review (non-blocking findings).** The restart test is named and
documented as a **context rebuild in one JVM** — it does not claim JVM-exit or crash
recovery. The provider-error log test now raises the service logger to DEBUG, first asserts
that the retry line with `AGENT_HTTP_422` was actually captured (so "no marker in the logs"
cannot pass on an empty list), then asserts the marker is absent, and restores the level.

**Verification (actually run):** `mvn clean verify` → **342 tests, 0 failures, 0 errors, BUILD
SUCCESS** (329 + 13); `LayeringGuardTest` 4/4 with no allowlist.

**Limits of this slice.**
- The result payload is provisional; Round 57 defines the draft/review contract.
- Capability rotation happens per claim; an expired lease that is reclaimed issues a new
  generation and therefore a new capability, which is what fences the old worker — but the
  Python side is not yet implemented, so "the old worker cannot call tools" is enforced on
  Java's internal endpoints only (55.4 brings the worker).
- Capabilities are not yet limited to a tool list (`allowed_tools`); the plan's per-task tool
  allowlist arrives with the appointment tool in Round 58.
- The internal endpoints are reachable without a JWT by design; the service token is the
  only process credential, so its configuration and rotation are an operational concern.

### Round 55.3 review fixes (second pass, 2026-10-22)

Three findings were re-checked; all three held, and each now has a test that failed before the fix.

**1. P1 — a dispatch acknowledgement could reopen a completed run.** The generation guard was there, but no source-state guard: `deliver` wrote `RUNNING` after the HTTP call returned, so a result that arrived *during* that call (WAITING_REVIEW, lease cleared, capability revoked) was overwritten with RUNNING — a row with no capability and no lease. The failure path had the same hole: a late timeout requeued a job whose result had been accepted.

Fixed by removing the acknowledgement entirely (the claim already sets RUNNING, so the successful dispatch has nothing left to write) and by requiring `state = RUNNING` in both `transition` and `retryLater`, alongside the generation. A refused write is now the expected outcome and is logged as such rather than treated as success.

Tests: the stub's handler calls the **real controller method** (`AgentInternalController.result(...)`, with the real authorization and transaction, but without going through HTTP) *before* it answers, then answers 202 — the run must end WAITING_REVIEW, with the capability gone; a second variant answers by closing the connection (the client sees a transport failure), and the run must **not** be requeued. The second test also asserts the retry path actually ran and was refused (a DEBUG line), so it cannot pass by never attempting the write.

**2. P2 — the service credential had no direction.** Java sent `properties.getServiceToken()` outbound and verified the same value inbound: a second header, not a second credential. Split into `app.agent.outbound-token` (`AGENT_OUTBOUND_TOKEN`, sent as the Bearer token to Python) and `app.agent.inbound-token` (`AGENT_INBOUND_TOKEN`, required on callbacks; blank still denies everything).

Tests: the dispatch request is asserted to carry the outbound token exactly; replaying the outbound token on a callback with a valid capability answers 401.

**3. P2 — an expired lease or deadline still authorized work.** `verify` checked the capability TTL (5 min), generation and RUNNING, but not the lease (60 s) or the task deadline, and `complete` had no time condition — so in the window before a reconcile pass, a worker whose lease had run out could still read context and submit a result. `verify` now refuses a lease or deadline that is not in the future, and the result UPDATE repeats both conditions so the check cannot be overtaken.

Tests: with the capability valid and the generation unchanged, setting the lease (and separately the deadline) into the past refuses both GET and POST and leaves the run RUNNING — no reconcile runs in the test. A further test drives the write itself (`AgentRunStateStore.complete`) after the lease has lapsed, which is the check-then-write window: the UPDATE refuses, so the guarantee does not depend on `verify` having run first.

**Red evidence (measured, then reverted).** With the guards temporarily removed: the mid-dispatch result was left **PENDING** instead of WAITING_REVIEW (the requeue clobber); the outbound token was accepted where 401 was expected; and the expired-lease call reached the authorization path instead of being refused. The three fixes restore all four tests.

**Verification (actually run):** `mvn clean verify` → **349 tests, 0 failures, 0 errors, BUILD SUCCESS** (342 + 7); `LayeringGuardTest` 4/4.

**Still not claimed.** The result payload stays provisional until Round 57. Java enforces these checks on its endpoints; the Python worker does not exist yet, so "a stale worker cannot call tools" is enforced here and not yet end-to-end. Per-tool allowlists arrive with the appointment tool (Round 58).

### Round 55.4 — Minimal persistent graph and the real worker (implementation, 2026-10-22; status In progress)

**Baseline:** `d11903ab`, clean tree, **349 Java tests / 69 Python tests**. Contract change:
`run-start.request` gained `execution_generation`.

**Contract.** The worker must know which generation it acts for: that is what it echoes on the
result, and what lets Java refuse a stale attempt. `execution_generation` is now a required
strict-positive field; both valid fixtures were updated, `run-start.invalid-missing-generation`
pins it on both sides, and Java's dispatcher DTO sends it (asserted in the dispatch test).

**The graph** (`medical-agent/src/medical_agent/graph/`): `validate_content → classify_message →
draft_reply → submit_result`, compiled with a LangGraph checkpointer. Classification is
keyword-derived for now but goes through the provider seam (async `invoke`), so Round 56 swaps the
classifier without moving the graph. The provider call gets its own loop because the graph is
synchronous and the endpoint runs in FastAPI's threadpool, which has none.

**The worker** (`worker.py`, `java_client.py`): one dispatch runs the graph and, from
`submit_result`, posts `{generation, outcome}` to Java's internal results endpoint with the
service's outbound token and the attempt's capability. Credentials live in a context variable for
the duration of the call — never in the graph state, so they cannot reach a checkpoint.
`process()` reconciles instead of blindly restarting: if the checkpoint already records this
generation as submitted it returns without work (a repeat dispatch produces no second draft), and
if the checkpoint is part-way through it resumes rather than starting over.

**The endpoint** (`POST /internal/v1/runs`): constant-time inbound-token check (401), required
capability header (400), contract validation (422), then the work; 202 is answered only after the
graph ran and the result was submitted.

**Checkpoints.** `MEDICAL_AGENT_CHECKPOINT_URL` selects the store: Postgres for deployment, SQLite
otherwise; `docker compose` starts Postgres next to the service. The Postgres saver and
`psycopg[binary]` are installed and the branch imports, but **no Postgres server exists here**, so
only the SQLite path is exercised.

**Tests (20 new; 71 total).**
- `test_graph.py` (6): classifies (`APPOINTMENT`), drafts and submits once with the right
  generation and exactly one provider call; a crash in `submit_result` is resumed by the next
  `process` without re-classifying (one provider call, two submissions); a repeat dispatch returns
  the same draft with no extra provider call or submission; empty content is refused before any
  model work; the checkpoint file contains neither the capability marker nor the outbound token.
- `test_checkpoints.py` (1): a **child process** writes a checkpoint and dies, then a **different
  process** resumes from that file and finishes — the classification still ran once.
- `test_api_runs.py` (5): an authenticated dispatch runs the graph and posts the result to
  `/api/v1/internal/agent/runs/41/results` with `X-Agent-Service-Token: <outbound>`,
  `X-Agent-Capability: <capability>` and `{"generation":1,"outcome":"APPOINTMENT:DRAFT_READY"}`;
  wrong inbound token 401, missing capability 400, missing generation 422, unknown field 422 —
  none reaching Java.

**Red evidence.** The graph first failed on `ValueError: 'draft' is already being used as a state
key` (a node name colliding with a state key), then on the provider signature (`ProviderResponse`
fields, async `invoke`) — design mistakes the tests forced out before anything was claimed. The
cross-process test first failed on the resume leg with `Connection refused`, which is exactly the
crash-then-resume behaviour it now asserts.

**Live probe (actually run):** uvicorn on `:8099` with both tokens and a stub Java server —
`POST /internal/v1/runs` answered `202 {"run_id":77,"status":"ACCEPTED"}` and the stub received
`/api/v1/internal/agent/runs/77/results` with `probe-outbound`, `probe-capability` and
`{"generation":2,"outcome":"APPOINTMENT:DRAFT_READY"}`.

**CI**: a workflow was written this round and ran once green on `4acaf31e` (backend, Python and
frontend jobs). It was then **removed by the owner** in `fb8b4134` ("Remove automatic CI workflow;
use local validation"), so there is no CI status to claim from that commit on. Validation is local
and is listed in the verification sections.

**Verification (actually run):** Java `mvn clean verify` → **349 tests, 0 failures**; Python
`pytest` → **71 passed**, `ruff` clean, `mypy --strict` no issues in 19 source files;
`docker compose config` valid with both tokens set.

**Limits.**
- The classification is a keyword mock and the draft a fixed template; Rounds 56/57 replace both.
- Postgres checkpoints and the compose stack are **unexecuted** here (no server, no daemon).
  SQLite checkpoints and the cross-process resume are what the evidence covers. The CI workflow was
  removed after one green run, so nothing about later commits is claimed from it.
- Graph-level failures are coarse: a validation or submission error leaves the run to Java's
  lease/deadline machinery instead of reporting a typed failure (56/57).
- The graph runs synchronously inside the request; a real provider would need the async execution
  the plan describes. Acceptable for the mock, not for production latency.
- Compose environment names changed with 55.3's direction split: Java's outbound token is
  `AGENT_OUTBOUND_TOKEN`, the service's inbound token is `AGENT_INBOUND_TOKEN`.

### Round 55.4 review fixes (2026-10-22, status In progress)

Four findings were re-checked; all four held, and the first two were blocking.

**1. P1 — a Java retry at a new generation resumed the old generation.** `process()` resumed any
unfinished checkpoint without comparing generations, so a run that failed at submission under
generation 1 came back as generation 2, resumed the old state and submitted `generation: 1` —
which the Java result endpoint refuses (409) forever, until the attempts ran out. The worker now
distinguishes four cases under a per-run lock: same generation already submitted (return it),
newer generation than the checkpoint (adopt the generation, then resume the pending node — or
re-submit the finished work — so the classifier and draft are not repeated), older generation
(refuse with `StaleExecution`, so a late worker cannot overwrite newer work), and an exact match
(resume). Identity fields (run, message, patient, conversation revision) are compared first; a
checkpoint for a different run is refused rather than adopted.

Evidence: `test_graph.py::test_a_newer_generation_takes_over_a_failed_submission` (one provider
call, submissions at generations 1 then 2, checkpoint records 2),
`test_an_older_generation_cannot_overwrite_newer_work`, and
`test_api_runs.py::test_a_java_retry_with_a_new_generation_takes_over_the_finished_work`, which
drives the real retry order against a Java-like receiver that refuses a stale generation: the
first dispatch answers 500 (its submission was refused), the second dispatch at generation 2
answers 202 and the receiver saw generations `[1, 2]`.

**2. P1 — the Postgres checkpointer's connection context was discarded.** `open_checkpointer`
returned `PostgresSaver.from_conn_string(url).__enter__()` and dropped the manager, so the
connection context could exit before `setup()` and the saver held a closed connection. The factory
is now a context manager for both backends (Postgres enters `from_conn_string`, SQLite closes its
connection in `finally`), and the application holds it for its lifetime through a FastAPI
`lifespan` + `ExitStack`: entered on startup, closed on shutdown. A dispatch that arrives before
the worker exists answers 503 instead of building one ad hoc.

Evidence: `test_api_runs.py::test_the_checkpointer_is_closed_when_the_application_stops` (the
connection answers inside the application and raises `ProgrammingError` after it stops), and the
SQLite tests and the resume helper now use the factory as a context manager.

**3. P2 — CI called a Maven wrapper that does not exist.** The repository has neither `mvnw` nor
`.mvn/`. The backend job now runs `mvn -B clean verify`. `setup-java` configures the JDK and the Maven cache; the `mvn` executable itself comes from the Ubuntu runner image.
**Still unverified:** GitHub Actions has not run this workflow — there is no runner in this
environment — so this is a correction to the configuration, not a green pipeline. Adding a real
wrapper would need the wrapper plugin's artifacts, which the sandboxed Maven cannot download.

**4. P2 — repeat-dispatch de-duplication was check-then-act.** `get_state()` → decide → `invoke()`
had no mutex, so two overlapping requests for one run could both enter the graph and classify,
draft and submit twice. Each run now has a `threading.Lock` guarded by a worker-level lock, taken
for the whole decision and execution, with the generation re-checked inside it. Java's claim
protects Java; this protects the Python worker within one process, which is the scope the review
asked for.

Evidence: `test_graph.py::test_two_concurrent_dispatches_of_one_run_do_the_work_once` — two
threads released from a barrier classify once and submit once.

**Also corrected.** The earlier 55.4 record claimed Done; the row and the round are now In
Progress, and the "Postgres/Docker/CI unexecuted" limits are repeated rather than implied.

**Verification (actually run, after the fixes):** Python `pytest` → **76 passed**; `ruff check .`
clean; `mypy --strict` no issues in 19 source files. Java's `mvn clean verify` (349 tests) last ran
on the unchanged Java tree; no Java file changed in these fixes.

**Still open before 55.4 can be accepted:**
- A real Postgres run (initialisation, two requests, rebuild-and-resume, connection release on
  shutdown): there is no Postgres server in this environment and no Docker daemon to start one.
- The GitHub Actions run for the newest commit (the `4acaf31e` run is green; `5d7920bc` and this
  one were pending at review time).
- An end-to-end Java↔Python retry with both processes running: the retry order is proven against a
  Java-like receiver, not against `AgentInternalController` itself.

### Round 55.4 review fixes (second pass, 2026-10-22)

**P2 — a failed submission after taking a generation over restarted the whole graph.** The
take-over path updated the checkpoint's `execution_generation` and then submitted *outside* the
graph. If that submission failed — or the process died between the update and the submission —
the next request for the same generation found `submitted_generation` still on the old attempt and
no pending node, so it fell through to a fresh `invoke(...)`: the message was classified and
drafted again.

The submission is no longer performed outside the graph. Taking over a generation re-enters the
graph at the draft node (`update_state(..., as_node="draft_reply")`), which schedules the
`submit_result` node, so the next step is a checkpointed submission and a failure leaves a pending
`submit_result` task. The same reconstruction is applied when the current generation's work is
finished but its submission is not recorded, which is the state a crash leaves behind.

**Evidence.** `test_graph.py::test_a_failed_submission_after_taking_over_a_generation_retries_only_the_submission`:
generation 1 completes and submits; generation 2 takes over and its submission fails; the same
generation 2 arrives again — one provider call, the same draft, submission generations `[1, 2, 2]`
and `submitted_generation = 2`. With the previous worker this test fails with
`assert 2 == 1` on the provider call count, which is the restart the review described.

**Doc correction.** The CI workflow comment now says what the action does: `setup-java` supplies the
JDK and the Maven cache, and `mvn` comes from the runner image.

**Status unchanged:** 55.4 stays **In progress** — a real Postgres run and a green GitHub Actions
backend job are still missing, as is an end-to-end Java↔Python retry.

**Verification (actually run):** Python `pytest` → **77 passed**; `ruff` clean; `mypy --strict`
clean. Java unchanged.

### Round 55.4 review fixes (third pass, 2026-10-22)

**P2 — a new generation took over an unfinished graph by declaring the draft complete.** The
take-over called `update_state(..., as_node=DRAFT_NODE)` whenever the generation changed, which
schedules the submit node as if drafting had finished. A checkpoint stopped earlier — the provider
failed during classification, or the process died before drafting — was therefore pushed forward
with no classification or draft, and the submission then failed on a missing key.

The take-over now reads the checkpoint instead of assuming it: a draft that exists with nothing
pending re-enters at the submit node (the case the previous pass fixed); any pending node —
validation, classification or drafting — is kept, and only the execution generation is adopted
before resuming; a checkpoint with neither a draft nor pending work is recomputed from the start
under the new generation rather than submitted half-built. The same draft check now guards the
"same generation, submission not recorded" branch.

**Evidence.** `test_graph.py::test_a_new_generation_resumes_a_failed_classification` (the provider
explodes under generation 1; generation 2 runs the classification again, produces the draft and
submits only generation 2) and `test_a_new_generation_resumes_from_the_draft_node` (a checkpoint
that finished classifying: the draft node runs before anything is submitted). With the previous
worker the first test fails with `assert 1 == 2` on the provider call count — the classification
never ran. The earlier `[1, 2, 2]` submission-retry test still passes.

**CI status.** The workflow's only green run was `4acaf31e` (all three jobs); `5d7920bc` was still
pending when the workflow was removed in `fb8b4134`. From that commit on, validation is local and
this record claims no CI result.

**Status unchanged:** 55.4 stays **In progress** (real Postgres run and an end-to-end Java↔Python
retry are still missing, and the newest commit's CI run is pending).

**Verification (actually run):** Python `pytest` → **79 passed**; `ruff` clean; `mypy --strict`
clean. Java unchanged.

### Round 55.4 — scope decision and end-to-end acceptance (2026-10-22)

**Scope decision (2026-10-22).** The current development and test environment is **Java H2 +
Python SQLite**: H2 holds the business data, `agent_run` and the capability digests; SQLite holds
the LangGraph checkpoints. Moving Python checkpoints to H2 is not required. A real PostgreSQL run
is **deferred and no longer blocks 55.4 or Round 55** — Postgres is a deployment option, not a
core deliverable of this project. The Postgres adapter is kept, and it is documented as
**not verified by an actual run** (no Postgres server or Docker daemon exists here). No database
migration, MySQL/PostgreSQL/Testcontainers or cloud deployment work is added in this round; those
are recorded as future, on-demand verification items.

**End-to-end acceptance (`AgentEndToEndIntegrationTest`, opt-in via `-Dagent.e2e=true`).** One
Spring context on a random port plus **two real uvicorn processes** started from the test, on H2
and one SQLite checkpoint file:

1. The first Python process is given a **wrong outbound credential**, so its result submission is
   refused by Java (401) and the dispatch fails (500 → `AGENT_HTTP_500`). The run stays PENDING with
   attempt 1 and generation 1, and the patient message is stored and readable through
   `GET /api/v1/patient/me/messages/2` — human messaging does not depend on the agent.
2. The first process is stopped; a second Python process starts with the **correct credential and
   the same checkpoint file**. `next_attempt_at` is moved into the past and the dispatcher claims
   the run again: attempt 2, **generation 2**.
3. The second process resumes its checkpoint and submits the result with generation 2; Java runs
   the result endpoint (service token + capability + generation + live lease + deadline) and the
   run reaches **WAITING_REVIEW**, with one run row for the message and no error left over.

The test also exposed a real defect: a successful result left the previous attempt's error in
`last_error`. `complete` now clears it, so the column means "the last failure that was not
followed by success".

**What this proves and what it does not.** It proves the two processes, the directional
credentials, the retry that increments the generation, the reuse of the checkpoint across Python
processes, the final state and the independence of human messaging. It does **not** prove by
itself that the resumed graph skipped its classification — that is proven by the Python worker
tests, which count provider calls — and it does not exercise Postgres.

**Verification (actually run).** `mvn -Dtest=AgentEndToEndIntegrationTest -Dagent.e2e=true test` →
**1 passed** (10.5 s, two Python processes). `mvn clean verify` → **350 tests, 0 failures, 1 skipped**
(the opt-in end-to-end test is skipped in the default run). Python `pytest` → **79 passed**, `ruff`
and `mypy --strict` clean. 55.1–55.3's evidence is unchanged.

**Validation, not CI.** The workflow had gained an `end-to-end` job for exactly this test, but the
owner removed the workflow in `fb8b4134` ("use local validation"). The acceptance is therefore run
locally, and the command is recorded so it can be repeated:
`mvn -Dtest=AgentEndToEndIntegrationTest -Dagent.e2e=true test` (plus `mvn clean verify`, `pytest`,
`ruff`, `mypy`).

**Unverified boundaries (named, not implied away).** PostgreSQL deployment (adapter kept, never
executed); Docker images (`docker compose config` only, no daemon here); a real DeepSeek model
(the adapter exists, no key or call in this environment); multi-instance workers (the lock is
per-process, as the review scoped it); CI (workflow removed — local validation only).

## Round 56 — Classification and versioned RAG (Done: 56.1–56.4 plus the 156-case evaluation set)

**Goal:** evidence-backed drafts with abstention, still staff-only. **Estimate:** 36–56 hours. Depends on Round 55.

| Slice | Priority / status | Work | Tests first / acceptance |
|---|---|---|---|
| 56.1 Classification and safety | P0 / **Done** (2026-10-23, after review) | Seven intents, three risk levels, multi-intent union with OTHER dropped, non-downgradable rules (14) matched on normalised text, clinical/emergency constraints from the final classification, fail-closed uncertainty via a strict answer envelope, DeepSeek adapter with bounded output/timeout, graph integration with review-only drafts | test_safety_routing.py (26) + test_deepseek_provider.py (6): typographic and line-break variants still hit a rule, a model-classified clinical question or emergency requires a clinician, malformed answers (including a non-array `intents`) escalate, the mock reports the specific intent, and the adapter's request shape, error masking and timeout are measured (see the 56.1 records) |
| 56.2 KB ingestion and release | P0 / **Done** (2026-10-23, tightened in the 56.3 review) | Approved synthetic policy corpus (5 documents), manifest with per-document approval and SHA-256, title-aware chunking whose title prefix counts toward the size limit, deterministic embedding baseline, version publish/withdraw with a (version, hash) approval snapshot | test_kb_ingestion.py (10): repeated ingestion is idempotent, an unapproved or tampered document is unavailable, a file with no manifest entry is ignored, a withdrawn version cannot be published, withdrawing a document takes the active version out of service, and publishing a new version deactivates the old one (see the 56.2 record) |
| 56.3 Retrieval and citations | P1 / **Done** (2026-10-23, after review) | BM25 lexical baseline, hashing dense baseline, RRF hybrid, per-search diagnostics (both rankings, RRF k, documents, lexical support), server-built citations with hash verification, graph retrieval node with `evidence_missing` / `evidence_conflict` | test_retrieval.py (11) + test_citations.py (6): a policy question finds its policy, a question with no shared vocabulary finds nothing and is flagged, the dense baseline never adds an unmatched chunk, an exact tie between two documents is reported as a conflict, a withdrawn corpus retrieves nothing, and every citation matches a retrieved chunk's id and hash (see the 56.3 record) |
| 56.4 Draft validation | P0 / **Done** (2026-10-28, after review) | Cited administrative drafts; per-citation support checks bound to the cited chunk; draft kinds (policy / routing / fallback) with separate rules; safe fallback with the original rejection reasons preserved and a hard block when the fallback is invalid | test_response_validation.py (9): a real draft cites its policy and passes, a clinical question is a routing summary with no policy citation, and fabricated citations, unsupported claims, oversized drafts, a missing notice and a poisoned chunk are all refused (see the 56.4 record) |

Build the first 150–200 manually checked synthetic cases alongside this round, not afterward. Split tuning and held-out tests by scenario/template family. Initial chunk sizes are experimental, not an accuracy claim. Keep real-model failures and costs visible.

### Round 56.1 — Classification and safety (Done, 2026-10-23)

**Baseline:** `ccb8dcc5`, clean tree, **79 Python tests** (+350 Java). No Java file changed in this
slice; the wire contract is untouched (`run-start.request` already carried `execution_generation`).

**The taxonomy.** Seven intents (`APPOINTMENT`, `PRESCRIPTION_REFILL`, `BILLING`, `LAB_RESULTS`,
`CLINICAL_QUESTION`, `ADMINISTRATIVE_REQUEST`, `OTHER`), three risk levels (`ROUTINE` < `URGENT` <
`EMERGENCY`) and a `Classification` model (`intents` 1–7, `risk`, `confidence` 0–1, `flags`,
`requires_clinician`) with `extra="forbid"`, so an answer that is not this shape is a failure to
handle, not something to guess around.

**Rules set a floor the model cannot lower** (`classification/safety.py`, 14 rules): emergency
phrases (chest pain, breathing, stroke signs, severe bleeding, self-harm, seizure) → `EMERGENCY`;
infant fever and pregnancy bleeding → `URGENT`; "should I stop taking / what dose / is it safe" →
`clinical_decision` + clinician; appointment, refill, billing and lab words contribute intents. The
merge is monotonic: `max(model risk, rule risk)`, a union of intents and flags, and a
`rule:raised_risk` flag whenever the rules actually overruled the model.

**Uncertainty escalates rather than guessing.** Any unreadable answer — not JSON, missing or
unknown fields, an unknown intent or risk, an empty intent list, a confidence outside 0–1 or of the
wrong type, an answer longer than the contract, a provider error or an unexpected exception — falls
back to the rule outcome with `uncertain_output`, confidence 0, `requires_clinician` and **at least
`URGENT`**. A failed provider therefore never yields `ROUTINE`.

**The DeepSeek adapter** (`providers/deepseek.py`) is wired into the factory, configurable through
`MEDICAL_AGENT_DEEPSEEK_{API_KEY,BASE_URL,MODEL}` and the request bound; it sends
`temperature: 0`, `max_tokens`, and reduces every failure to `ProviderError` carrying a status or a
fixed phrase — never the response body, which can quote the patient's message.

**The no-model deployments still route.** The classifier asks the provider for the contract, and
the built-in `MockProvider` now answers that prompt with a valid `OTHER/ROUTINE/0.5` object (other
prompts are still echoed), so `MEDICAL_AGENT_PROVIDER=mock` produces normal routing decisions
instead of escalating every message to `uncertain_output`.

**Graph integration.** `classify_message` now runs the classifier and stores intents, risk, flags,
confidence and `requires_clinician`; `draft_reply` writes a review-only draft ("A member of the care
team will review your message…" whenever a clinician is required) and sets `review_required: true`
on every v1 output; the submitted result's provisional outcome now carries the routing decision
(`<INTENT>:<RISK>:DRAFT_READY`).

**Tests (25 new; 105 total).** `test_safety_routing.py` (18): mixed intent keeps the highest risk
and flags the raise; a model cannot downgrade a stroke phrase; a clinical decision is flagged and
never `ROUTINE`; a routine message stays routine; eight malformed answers escalate; a provider
failure and an unexpected exception escalate; an uncertain answer on chest pain still keeps
`EMERGENCY`; all seven intents are accepted; the graph drafts a clinician review with
`review_required`; empty content is refused before any model call. `test_deepseek_provider.py` (6) plus one mock-routing test:
request shape (path, bearer, model, `max_tokens`, `temperature: 0`), a non-200 answer becomes a
failure without the body, a wrong-shaped or empty answer fails, a slow model times out, and an
unreachable host fails.

**Red evidence.** Three failures shaped the design: the graph node called a provider method that did
not exist (`AttributeError: 'RecordingProvider' object has no attribute 'invoke_now'`), which added
the synchronous entry point the graph needs; `confidence: 1.5` escaped as a Pydantic error instead
of escalating, so the range check moved into the answer parser; and a provider `RuntimeError` was not
caught, so the classifier now fails closed on any exception. A take-over test also broke, correctly:
provider failures no longer stop the graph, so that test now injects the failure at the classifier
node, which is the layer that can still leave a checkpoint mid-way.

**Verification (actually run).** `pytest` → **105 passed**; `ruff check .` → clean; `mypy --strict` →
no issues in 23 source files. Java: `mvn clean verify` → **350 tests, 0 failures, 1 skipped**, and the
opt-in dual-process test still passes against the real service (`-Dagent.e2e=true`, 12.0 s), which is
also what proves the mock path routes rather than escalating.

**Limits.**
- The rules are conservative keyword approximations on normalised text: they can raise a false
  positive (which escalates a routine message) and they cannot promise to catch every paraphrase of
  an emergency. The failure direction is deliberate; exhaustive coverage is not claimed.
- `confidence` is model-reported and uncalibrated; the classifier only uses it as data, never as a
  publication gate.
- The DeepSeek adapter is measured against a local stub; no real API call was made here (no key in
  this environment).
- The plan asks for the first 150–200 manually checked synthetic cases alongside this round. They
  are **not** written yet; that remains open for 56.2–56.4 and is recorded rather than claimed.

### Round 56.1 review fixes (2026-10-23)

Four findings were re-checked with the committed classifier; all four held.

**1. P1 — typography and line breaks bypassed the emergency rules.** The rules matched the raw
string, so `I can’t breathe` (curly apostrophe) and `chest` + newline + `pain` stayed ROUTINE —
ordinary ways of typing the same sentence. The rules now match a normalised copy (Unicode quotation
marks, non-breaking spaces, en/em dashes and collapsed whitespace); the message itself is unchanged
for the model, the draft and the result. Tests cover four variants; the roadmap's "cannot miss a
badly-worded emergency" claim is **deleted** — a finite keyword list cannot promise that, and the
document now says the rules can only be conservative, not exhaustive.

**2. P2 — a model-classified clinical question could be "no clinician needed".** `requires_clinician`
came only from rule hits. The final classification now sets it: a `CLINICAL_QUESTION` intent raises
the risk to at least URGENT and requires a clinician, and any EMERGENCY requires one, whatever the
model's flags. Two tests cover model-only answers that no rule matches.

**3. P2 — `intents` was accepted as an object.** The merge iterated `parsed["intents"]`, so
`{"intents": {"BILLING": true}}` became `["BILLING"]`. The raw answer is now validated by a strict
Pydantic envelope (`extra="forbid"`, `strict=True`, `intents: list[str]` with 1–7 items,
`confidence` a number in 0–1) before anything is merged; a wrong-shaped answer takes the
`uncertain_output` path. Four new bad payloads are asserted (object, bare string, list of objects,
eight entries).

**4. P2 — OTHER could be the primary intent.** The merge kept the model's order, so `[OTHER,
APPOINTMENT]` reported OTHER, and the mock (which answers OTHER) made every specific intent
disappear from the result. OTHER is now dropped whenever a specific intent exists, and the primary
intent is the first specific one. Three graph tests check the submitted outcome for appointment,
billing and refill messages: `APPOINTMENT:ROUTINE:DRAFT_READY`, `BILLING:…`,
`PRESCRIPTION_REFILL:…`. The outcome string moved into `outcome_for(state)` so it can be asserted
without an HTTP call.

**Verification (actually run).** Python `pytest` → **118 passed**; `ruff` clean; `mypy --strict`
clean (23 source files). `mvn clean verify` → **350 tests, 0 failures, 1 skipped**; the opt-in
dual-process test passes (11.2 s). The 150–200 manually checked synthetic cases remain open, as the
review allowed, and their first boundary set is now these four families.

### Round 56.2 — KB ingestion and release (Done, 2026-10-23)

**Source material.** The plan asks for an *approved synthetic* corpus, so the repository ships
five short practice-policy documents written for this project (appointments, prescription refills,
billing and insurance, laboratory results, privacy and consent — about 900 words in total). They are
modelled on public patient-education topics rather than copied from anyone: the licensing check
behind that decision is that US federal works are public domain, MedlinePlus explicitly permits
reuse with attribution ([MedlinePlus: using content](https://medlineplus.gov/about/using/usingcontent/))
and NHS content is available under the Open Government Licence
([NHS terms](https://archive.datadictionary.nhs.uk/DD%20Release%20November%202025/notices/terms_and_conditions.html),
[HEE guidance](https://support.library.hee.nhs.uk/support/solutions/articles/27000124231-Can-I-use-content-from-NHS-websites-in-publications-)).
No third-party text, and no real patient data, is committed — the manifest records that provenance in
its `corpus` field and a test asserts it.

**What was built** (`medical-agent/kb/`, `medical-agent/src/medical_agent/kb/`):

| Piece | Behaviour |
|---|---|
| `kb/manifest.json` | Per-document `id`, `title`, `version`, `status` (`approved`/`withdrawn`), `approved_by`, `approved_at`, `tags`, `file`, `sha256`; ids unique, approved entries must name an approver |
| `loader.py` | Loads only approved entries; a missing file or a hash mismatch is a `CorpusError` (an edited document must be approved again); withdrawn entries are reported as skipped, files with no manifest entry are never read |
| `chunking.py` | Splits Markdown by headings, carries the document title and heading path into every chunk, bounds chunks at 600 characters on sentence boundaries, and derives `id = <doc>:<version>:<index>` plus a SHA-256 of the chunk text — stable across runs |
| `embedding.py` | `hash64-v1`: tokens hashed into 64 dimensions, L2-normalised, deterministic and dependency-free; explicitly a **baseline**, to be replaced behind the same function when 56.3 compares retrieval baselines |
| `store.py` | `ingest()` builds a version whose id is a digest of the approved documents and the embedding version, so repeated ingestion returns the same version and chunks; `publish()` requires every document to be *still* approved with the same hash; `withdraw()` removes a version; `active()`/`chunks()` re-check the manifest and return nothing if an approval was withdrawn |

**Tests (`test_kb_ingestion.py`, 10).** Repeated ingestion is idempotent (one version, identical
chunk ids and hashes); an unapproved document is absent from the version and reported as skipped;
a document file with no manifest entry is ignored; a tampered file is refused against its approved
hash; a withdrawn version cannot be republished; withdrawing a document after publication takes the
active version out of service and publishing it again is refused; publishing a new version
deactivates the previous one; chunks start with the title, carry a heading path and stay bounded;
the corpus is synthetic and under 2 000 words; the embedding baseline is deterministic, 64
dimensions, and labelled.

**Verification (actually run).** `pytest` → **128 passed**; `ruff check .` → clean; `mypy --strict` →
no issues in 28 source files. Java untouched (350 tests, 1 skipped from the last run); the dual-process
test does not exercise the knowledge base yet, which is 56.3's job.

**Limits.**
- The embedding is a hashing baseline, not a semantic model: it proves the plumbing, not retrieval
  quality. 56.3 measures lexical vs dense vs hybrid with this baseline explicitly named.
- The store is in memory; nothing is persisted between processes yet (the plan's Postgres knowledge
  store is a deployment option by the 2026-10-22 decision, together with the deferred PostgreSQL
  verification).
- Five short documents are enough for plumbing and retrieval-baseline tests; they are **not** the
  150–200 manually checked evaluation cases, which remain open for 56.3/56.4.

### Round 56.1 review fixes (second pass, 2026-10-22/23)

**1. The Docker build context now excludes local secrets and runtime state.** `.gitignore` does
not apply to Docker, and `.dockerignore` did not list them: it now excludes `.env`, `.env.*`
(keeping `.env.example`) and `.agent/`, so a future `COPY . .` cannot carry a key or a checkpoint
into an image.

**2. The Java dispatch timeout now covers the real model call, and the configuration refuses an
incoherent combination.** With the service calling a real model, a 5 s Java timeout against a 30 s
model call meant "Java gives up and re-dispatches while the model is still working". The defaults
are now `request-timeout: 60s` and `lease: 180s` (the lease must outlive the timeout, or reconcile
reclaims a job in flight), `AgentDispatchProperties.validate()` refuses `lease <= request-timeout`
or `lease >= deadline` at startup, and both the YAML and the class defaults agree.

Evidence: `AgentDispatchPropertiesTest` (4) pins the invariant and the documented 30 s model
timeout; `AgentSlowDispatchIntegrationTest` answers a dispatch after 6 s — slower than the old
default, inside the configured timeout — and asserts the run is `RUNNING`, the lease is still live
and `reconcile()` does not reclaim it. The two older timing tests that depended on a lease shorter
than the timeout were rewritten: a slow dispatch keeps its lease now, and the per-claim lease test
compares the two leases instead of relying on an expiring one.

**3. README no longer mentions `./mvnw`.** This repository has no wrapper; the local instruction is
`mvn spring-boot:run`, and the timeout/lease relationship is documented next to the token pairing.

**Verification (actually run).** `mvn clean verify` → **355 tests, 0 failures, 1 skipped**; the
opt-in dual-process test passes (10.5 s). Python unchanged (128 passed, ruff/mypy clean).

**Note on the key:** the full value was shared in this session's conversation. It is not in any
commit (`git log -S` and the staged diff are clean) and `.env` is gitignored with mode 600, but
rotation is the user's decision, and rotating only requires updating the local `.env`.

### Round 56.3 — Retrieval and citations (Done, 2026-10-23)

**Baseline:** `5b324b70` + Round 56.2's corpus, clean tree, **128 Python tests**. Implemented
code-first this round, by instruction, then tested.

**What was built** (`medical-agent/src/medical_agent/kb/`):

| Piece | Behaviour |
|---|---|
| `retrieval.py` | BM25 (k1=1.2, b=0.75) over content tokens with stopwords removed; a dense baseline over the `hash64-v1` embedding; reciprocal rank fusion (k=60); `SearchResult` carries hits plus diagnostics (`query_tokens`, both rankings, `rrf_k`, `embedding_version`, `documents`, `lexical_support`) and a `conflict` flag |
| `citations.py` | `Citation` = chunk id, document, version, title, heading path, content hash; `build_citations` (deduplicated, limited to 3) and `verify_citations`, which refuses any citation that was not retrieved or whose hash no longer matches |
| graph | a `retrieve_evidence` node between classification and drafting stores `evidence`/`citations` and appends `evidence_missing` or `evidence_conflict`; the draft names its sources ("Sources: …") or says plainly that no policy was found |
| app | the approved corpus is ingested and published at startup from `MEDICAL_AGENT_KB_MANIFEST_PATH` (default `kb/manifest.json`); a broken corpus stops the service loudly; tests and deployments without a manifest simply run without retrieval |

**Design decision worth recording: the dense baseline only re-ranks.** A hashing embedding has no
semantics, so on its own it "retrieves" something for any query at all — including nonsense. The
hybrid therefore fuses the lexical result with a dense re-ranking of *the same candidates*; a
question with no shared vocabulary returns nothing and is flagged `evidence_missing`. A real
embedding model can relax that rule later, and the diagnostics keep both lists visible so the
change is measurable.

**Escalation.** "Empty or conflicting evidence escalates" means the draft is flagged for staff and
never presented as an answer: `evidence_missing` produces "I could not find a practice policy…",
and a conflict (two different documents tied for the best match) adds `evidence_conflict`. Nothing
in this round publishes; every draft remains `review_required`.

**Tests (17 new; 144 total).** `test_retrieval.py` (11): billing and refill questions find their
policies; nonsense and empty queries return nothing; the limit holds; both baselines and the RRF
constant are reported; every returned chunk has lexical support; an identical-text tie across two
documents is flagged; a withdrawn corpus retrieves nothing; the graph attaches citations and flags
missing evidence with a safe draft. `test_citations.py` (6): citations match retrieved chunks;
forged ids and tampered hashes are refused; deduplication and the limit hold; `describe` names the
title and heading.

**Verification (actually run).** `pytest` → **144 passed**; `ruff check .` clean; `mypy --strict` no
issues in 30 source files; the opt-in dual-process test still passes against the real service
(10.7 s), which is what exercises the app's startup ingestion. Java untouched (355 tests, 1 skipped
from the last run).

**Limits.**
- Retrieval quality is measured on five short synthetic documents: the baselines are plumbing, not
  a ranking claim. The lexical baseline is the only one that can stand alone; the dense one cannot
  until a real embedding model replaces `hash64-v1`.
- Conflict detection can only see an exact tie; a semantic disagreement between policies is not
  detectable with these baselines and is not claimed.
- Citations are server-built and verified, but the draft text is still a fixed template; whether a
  draft actually *uses* the evidence is Round 56.4's validation.

### Round 56.3 review fixes (2026-10-23)

Four findings were re-checked with probes against the committed code; all four held. The two
open 56.2 items were fixed in the same pass because the retriever depends on them.

**1. P1 — a running retriever outlived a withdrawal.** The application read `knowledge.chunks()`
once at startup and kept that snapshot, so withdrawing a version afterwards changed nothing until
a restart. `Retriever` now takes a **source** (`KnowledgeBase.chunks`, a callable) and re-reads it
on every search, rebuilding its index when the (chunk id, hash) signature changes; a withdrawn or
unpublished corpus returns no hits with `reason: "no approved corpus is published"`. The application
passes the callable. Test: create one long-lived retriever, withdraw the version, and the same
instance returns nothing.

**2. P2 — citation verification checked only two fields.** `verify_citations` compared the chunk id
and the content hash; a citation with the right id and hash but a forged document, version, title or
heading path passed and `describe()` then printed the forged source. It now compares **every** field
against the retrieved chunk, and the executed graph path calls it after `build_citations`, so the
"verified" claim is no longer limited to a helper's tests. Tests: each of document, version, title
and heading path tampered individually is refused; real citations pass.

**3. P2 — no retriever skipped the missing-evidence path.** With no corpus loaded the node returned
empty evidence and no flag, so a billing question got the ordinary template. "No retriever" and "zero
hits" now take the same path: `evidence_missing`, and the draft says it could not find a practice
policy. Test: a run without a retriever is flagged and produces the follow-up draft (the existing
unpublished and zero-hit cases cover the other two roads to the same flag).

**4. P2 — the conflict check only looked at the first two chunks.** For A, A, B with equal scores the
first two both came from A and the conflict was missed. `_tied_documents` now inspects the **whole**
top-score group and reports a conflict when it spans more than one document; a tie inside one document
stays non-conflicting. Both cases are tested.

**56.2 items, fixed in the same pass.** The release now stores the `(id, version, sha256)` approval
it was built from and `_require_still_approved` compares all three, so re-approving a document at a
new version invalidates the old release (test: the public version stops serving). And the chunker
subtracts the title/heading prefix from the size budget, so `len(chunk.text) <= 600` genuinely holds
(the test's earlier `+120` tolerance is gone).

**Verification (actually run).** `pytest` → **155 passed**; `ruff` clean; `mypy --strict` clean (30
source files); the opt-in dual-process test passes (10.7 s). Java unchanged (355 tests, 1 skipped
from the last full run).

### Round 56.3 review fixes (second pass, 2026-10-23)

**1. P2 — the retriever rebuilt its index in place, so concurrent searches could mix two
versions.** `_refresh()` replaced `_chunks_by_id`, the token maps and the vectors one field at a
time while a search could already be reading them; two runs dispatch concurrently, and the worker's
per-run lock does not cover the shared retriever. Measured against the previous implementation, the
new concurrency test fails with `KeyError('old-policy:1:000')` and `KeyError('new-policy:1:000')`.

The index is now an immutable `_Index` snapshot (frozen dataclass, read-only mappings) built once
per corpus signature and swapped atomically under a lock; `search()` takes one snapshot and uses
only it. Tests: a search paused after its lexical scores finishes on the index it started with while
another search sees the new corpus, and four threads searching while the corpus flips never raise
and never return chunks from two corpora in one result.

**2. P2 — a long title or heading path could still exceed 600 characters.** The chunker kept at
least 50 characters of body by construction (`room = max(50, …)`), which pushed the chunk past the
limit whenever the prefix was long. The displayed prefix now has its own budget: it is shortened
with an ellipsis so the total chunk stays within 600, while the metadata keeps the full title and
heading path, and the body is never truncated (only split at sentence boundaries).

Tests: the review's case (200-character title, two 200-character headings, 100-character body)
produces chunks within the limit with the full title/path in metadata and the body present; a
1 500-character body is split into chunks that all fit and whose bodies concatenate back to the
original text.

**Verification (actually run).** `pytest` → **159 passed**; `ruff` clean; `mypy --strict` clean (30
source files). The dual-process test last passed on the previous commit (10.7 s); Java unchanged.

### Round 56.4 — Draft validation (Done, 2026-10-28)

**Baseline:** `522cfc66`, clean tree, **159 Python tests**. Implemented code-first, then tested.

**What was built.**

| Piece | Behaviour |
|---|---|
| `drafting.py` | An administrative draft quotes the first sentence of the best retrieved chunk and names it (`[chunk:<id>]`), then carries the fixed safety notice. A clinical question gets a routing summary (care team will respond; call 911 if emergency) and no policy citation; no evidence gives the "no policy found" draft. |
| `validation.py` | Checks, as a list of reasons: draft too long (> 1 200) or too short, missing safety notice, fabricated citation (a `[chunk:…]` that is not among the retrieved citations), missing citation on an evidenced draft, unsupported claim (under 60% lexical overlap with the evidence), instructions in the retrieved text, a clinical question answered as an administrative one or carrying a policy citation, and instruction-like text in the draft itself. |
| graph | The draft node drops a poisoned chunk before drafting, records `untrusted_evidence`, validates the draft, and on any reason falls back to the safe message, appends `draft_rejected`, and stores `validation: {ok, reasons}` in the checkpoint. Every path keeps `review_required: true`. |

**"Supported" is lexical, and that is the limit.** The support check asks whether a sentence's content
tokens overlap its cited evidence by 60%; it catches a sentence that drifts off the policy and does
not catch a careful paraphrase that reverses meaning. Entailment is not implemented and is not
claimed. The fixed sentences (routing summary, no-policy text, safety notice) are excluded from the
check, because they are instructions to the reader rather than claims about a policy.

**Tests (`test_response_validation.py`, 9).** A billing draft cites its chunk and passes with no
reasons; a chest-pain message produces the routing summary with no citations and `ok`; a fabricated
chunk id, an invented claim ("we accept cryptocurrency"), an oversized draft, a missing notice and a
poisoned chunk are each refused, with the poisoned case also asserting the instruction text never
reaches the draft; `contains_instructions` recognises the injection phrases it is meant to.

**Verification (actually run).** `pytest` → **167 passed**; `ruff` clean; `mypy --strict` clean (32
source files); the opt-in dual-process test passes (10.2 s). Java untouched (355 tests, 1 skipped from
the last full run).

**Limits.**
- The draft is a deterministic template over one quoted sentence, not a generated answer: the
  model is not asked to write, which is why validation has something traceable to check. A generated
  draft would need the same validator plus its own evidence tests.
- The validator's support test is lexical; see above.
- The draft, citations and validation live in the Python checkpoint. Java still receives only the
  provisional outcome string, so none of this is visible to staff yet — Round 57's review queue is
  where they surface, together with the result contract that carries them.
- The 150–200 manually checked evaluation cases are still not written; they remain an open
  follow-up of Round 56, recorded rather than claimed.

### Round 56.4 review fixes (2026-10-28)

**1. P2 — the fallback could itself fail validation and still be submitted, and it overwrote why.**
The node re-validated the fallback while the rejected draft's evidence was still attached, so the
fallback failed `missing_citation`, and the submit node never consulted `validation.ok`. The draft
now has a **kind** (`policy_answer`, `routing_summary`, `no_policy`, `safe_fallback`), and the kind
decides the rules: only a policy answer requires a citation. The fallback is validated with
`require_citation=False`, the original reasons are kept in `validation.rejected_reasons`, and if the
fallback is *also* invalid the node raises `DraftBlocked` so nothing is submitted; the submit node
carries the same guard. Tests: the original `missing_citation` survives the fallback, and an unusable
fallback blocks the run.

**2. P2 — support was checked against the whole retrieved set, not the cited chunk.** The citation
marker now sits inside the sentence it supports, and the check compares that sentence with **only**
the chunks it cites. Test: a billing claim citing the appointment policy is refused even though the
billing chunk was also retrieved; the same claim citing billing passes.

**3. P2 — short claims were skipped.** Sentences under four content tokens were exempt. They are now
checked too: a short claim must appear verbatim in the chunk it cites, otherwise it is refused. Test:
"We accept cryptocurrency" with a real citation is refused; "Payment plans are available" that the
chunk contains passes.

**4. P2 — the clinical summary kept the retrieved citation metadata.** The state now separates
`citations` (what retrieval found, diagnostics and the fabrication whitelist) from
`adopted_citations` (what the draft relies on). A routing summary and the safe fallback adopt none.
Test: a chest-pain message that also matches a policy keeps `citations` as diagnostics and
`adopted_citations == []`.

**Also fixed in passing:** the draft node's own flags (`untrusted_evidence`, `draft_rejected`) were
computed on a working copy and never written back to the checkpoint; `_draft_result` now returns them.
That is why the poisoned-chunk test initially saw no `untrusted_evidence` flag.

**Round 56 status.** The code for 56.1–56.4 is done, but the plan asks for 150–200 manually checked
synthetic cases built alongside this round, and they are not written. Round 56 therefore stays **In
progress** with that evaluation set as an independent acceptance task; 174 automated tests are not a
substitute for it.

**Verification (actually run).** `pytest` → **174 passed**; `ruff` clean; `mypy --strict` clean (32
source files); the opt-in dual-process test passes (10.5 s). Java unchanged.

### Round 56.4 review fixes (second pass, 2026-10-28)

**1. P2 — the safety-notice prefix bypassed every check.** `_claims()` skipped any sentence that
*started* with "Prepared for staff", so `Prepared for staff review; We accept cryptocurrency
[chunk:invented:9:9].` was never examined: a fabricated claim and a fabricated citation in one
sentence. Only an **exact** fixed sentence is exempt now, and citation validity is checked over the
whole draft rather than only inside extracted claims, so the marker is caught wherever it sits.
Tests: the payload is refused as `fabricated_citation` plus an unsupported/unexpected claim; the
real notice still passes.

**2. P2 — the submit guard let missing or partial validation through.** It blocked only
`ok is False`, so no record, `None`, `{}` and `{"ok": null}` all reached the submit function - and a
checkpoint written before the validator existed could resume straight to submission. The guard now
requires an explicit `ok is True`. A legacy checkpoint stopped at submission is **re-entered at the
retrieval node** so drafting and validation run again, rather than being submitted or blocked
forever. Tests: five malformed records are refused and a passing one submits; a legacy checkpoint
resumes, validates and submits exactly once.

**3. P2 — whitespace variants hid a known injection phrase.** `contains_instructions` matched literal
spaces, so `Ignore\u00a0previous\u00a0instructions` was not detected. The check now runs on a
whitespace-normalised copy (the original text stays untouched for evidence and audit) and the regex
matches the normalised form. Tests: NBSP, newline and repeated-space variants are detected, and a
chunk poisoned with NBSP is dropped as `untrusted_evidence` and never reaches the draft.

**Regression found while fixing (2):** adopting a new generation for a checkpoint whose submission is
pending must keep that submission next. `update_state(..., as_node=DRAFT_NODE)` does that; a plain
`update_state` recomputed the next tasks and silently dropped the pending submit. Both the unit
`[1, 2, 2]` case and the HTTP retry case caught it.

**Verification (actually run).** `pytest` → **186 passed**; `ruff` clean; `mypy --strict` clean (32
source files); the opt-in dual-process test passes (10.4 s). Java unchanged.

### Round 56.4 review fixes (third pass, 2026-10-28)

**P2 — "re-enter at the retrieval node" actually skipped retrieval.** `update_state(..., as_node=
RETRIEVE_NODE)` marks that node *finished*, so the graph went straight to drafting: a legacy
checkpoint with no evidence produced the no-policy reply even when the corpus held the policy, and a
checkpoint holding a withdrawn chunk drafted from it without the current approval check. The resume
now schedules from the classification node, which makes `retrieve_evidence` the **next** step, keeps
the saved classification (no extra model call) and replaces stale evidence with what the current
corpus returns.

Tests: a legacy checkpoint with empty evidence resumes, retrieves, adopts the current billing chunk
(`adopted_citations[0].document_id == "billing-and-insurance"`), validates and submits once, with the
provider call count at **zero**; a legacy checkpoint holding `retired-policy` evidence is replaced -
no retired document survives in `evidence`, `citations`, `adopted_citations` or the draft.

**Verification (actually run).** `pytest` → **187 passed**; `ruff` clean; `mypy --strict` clean (32
source files); the opt-in dual-process test passes (11.2 s). Java unchanged.

### Round 56 evaluation set (Done, 2026-10-28)

**What exists.** `medical-agent/evals/cases.jsonl` holds **156 cases** in six families, generated by
`evals/build_cases.py` from hand-written expectation tables and run by `evals/run.py`:

| Family | Cases | What is asserted |
|---|---|---|
| classification | 65 | each of the seven intents; six emergency and two urgent rules; six mixed-intent messages that keep the highest risk; five clinical decisions; routine messages; a model-classified emergency; rule phrasings such as "pressure in my chest", "passed out", "newborn fever" |
| uncertainty | 21 | eleven malformed answers (missing field, unknown field/intent/risk, empty intents, confidence out of range or wrong type, an object or string instead of an array, eight intents), a provider error, and three uncertain answers on emergency text that must keep the rule floor |
| retrieval | 32 | three queries per policy document; no-vocabulary queries return nothing; limit; a three-way tie across two documents is a conflict; a single-document tie is not |
| citations | 7 | a valid citation passes; document, version, title, heading and hash forgeries are refused; a chunk that was not retrieved is refused |
| validation | 17 | a valid policy draft; fabricated citation; unsupported and short unsupported claims; missing notice; oversized; a claim bound to the wrong chunk; two-chunk drafts; poisoned evidence with spaces and with a non-breaking space; instructions in the draft; routing drafts that claim or cite |
| graph | 14 | which draft kind each message produces, which citations are adopted, `review_required`, the clinician flag, and the missing-evidence path with and without a retriever |

**Result: 156/156 pass**, run with `PYTHONPATH=src .venv/bin/python evals/run.py`. The runner is
deterministic (mock or canned model answers), so a failure is about our code or our expectation. It
is kept out of the unit suite on purpose.

**Evaluator defects found in review, fixed here.** Importing `build_cases` used to rewrite the JSONL,
so the consistency guard read the file the import had just written and could not notice tampering;
the write now lives in `main()` behind `if __name__ == "__main__"`, and the guard test proves a
tampered and a truncated copy no longer match plus that importing leaves the file's hash unchanged.
The citation family also treated *any* `CitationError` as a pass, so a validator that refuses every
citation would have looked green; each kind now decides what an exception means, and
`tests/test_evals_runner.py` drives the runner with a validator that accepts everything and one that
refuses everything, requiring both to fail the right cases.

**Split, stated plainly.** Every case carries `split: "dev"`. The set was used while the code was
being built and fixed, so it is a **deterministic regression set**: 156/156 is evidence that the
promised rules, retrieval plumbing, citations and validation still behave, and it is **not** a
generalisation or accuracy claim. A held-out split that was never run during development is an open
item, recorded rather than simulated.

**What the evaluation found — three real defects, fixed here:**
1. A quoted policy sentence kept the source's line break, so the validator saw a claim without its
   citation and a refill or lab draft fell back to the safe message. The quoted sentence is now
   rendered on one line.
2. The lexical baseline had no stemming, so "book" missed "booked" and "record" missed "records";
   "How do I book a visit?" retrieved the refill policy. A deterministic suffix strip was added to
   `tokenize`.
3. The self-harm rule matched "end my life" but not "ending my life", a common phrasing that would
   have been routed as routine. The rule now accepts both.

**Honest meaning of "manually checked".** The expectations were written from the contract by hand
and each was reviewed here; they are not a clinical review and no external expert saw them. The
model answers are mock or canned, the corpus is five short synthetic documents, and no case claims
accuracy against a real model — the set protects the deterministic rules, retrieval plumbing,
citations and validation, which is what Round 56 built.

**Verification (actually run).** `evals/run.py` → **156/156**; `pytest` → **190 passed** (187 + the
three dataset guards); `ruff` clean; `mypy --strict` clean (32 source files). Java unchanged.

## Round 57 — Staff review and safe publication (In progress: 57.1 in re-review, 57.2–57.3 Pending)

**Goal:** first complete business demo: patient → safety/routing → cited draft → staff edit/approval → patient reply → audit. **Estimate:** 24–40 hours. Depends on Rounds 54–56.

| Slice | Priority / status | Work | Tests first / acceptance |
|---|---|---|---|
| 57.1 Review queue | P0 / **In progress** (2026-10-28; review fixes applied, awaiting re-review) | Result contract v1 (`run-result.request`) persisted to `agent_run_result`; staff queue with draft/risk/reason/citations; claim exclusive per run; human-only items when the agent produced nothing; scope and role enforcement | AgentReviewAuthorizationTest (5): a patient cannot read the queue (403), a doctor outside the patient's scope sees nothing and cannot claim (403), the reviewing doctor sees draft + citations and is the only claimant (409/403 for another), an answered thread leaves the queue, and an agent failure keeps the message as a human-only `RUN_PENDING` item (see the 57.1 record) |
| 57.2 Approve/edit/reject | P0 / Pending | Explicit approve-and-send; record original/final text and actor; reject does not close the patient's unresolved request | AgentReviewIntegrationTest: concurrent approval publishes once; edited content is the published content; rejected draft remains actionable manually |
| 57.3 Freshness and recovery | P0 / Pending | Conversation/result version check; human reply invalidates old draft; reliable graph resume after Java publication | ReviewConcurrencyIntegrationTest: new message prevents stale approval; Python outage does not block staff send; recovery never resends |

Use an atomic business transaction for review decision and message publication; do not hold it over a Python request. Record durable agent_action_event entries for decisions and denied/failed actions, not just successful AOP calls. Any projection to the existing async audit must have deduplicated/recoverable delivery if it is claimed as reliable.

### Round 57.1 — Review queue (Done, 2026-10-28)

**What was built.**

| Piece | Behaviour |
|---|---|
| Result contract (`contracts/agent/v1/run-result.request.schema.json`) | `contract_version`, `run_id`, `generation`, `intent`, `risk`, `requires_clinician`, `draft` (≤1500), up to 8 citations (chunk id, document, version, title, heading path, 64-hex content hash) and the validator's `{ok, kind, reasons, rejected_reasons}`. Pydantic owns it; Python fixtures and the Java fixture test read the same file |
| Python submission | `java_client` now sends the whole result (the adopted citations only, so a routing summary carries none), derived from the graph state |
| Java storage | `agent_run_result` (schema v8) written in the result endpoint, with `validation_reasons`/`rejected_reasons` and the citations stored as JSON in the queue's shape |
| Review queue | `GET /api/v1/agent/reviews` lists, for each thread where the patient spoke last, an item with the draft, risk, citations and validation; when the run is not `WAITING_REVIEW` or has no result, the item is human-only with `reason` `NO_RUN` / `RUN_FAILED` / `RUN_PENDING` / `NO_RESULT` and `draft: null` |
| Claim | `POST /api/v1/agent/reviews/{runId}/claim` is exclusive (`agent_review_claim`, unique per run); the same reviewer may re-claim, another gets 409, a doctor outside the scope gets 403 |
| Authorization | `@PreAuthorize("hasAnyRole('ADMIN','DOCTOR')")` on the controller and `DoctorPatientScope.requireAccess`/`permits` per item, so a patient is refused and a doctor sees only their own patients; ADMIN (unscoped) sees all patient threads |

**Tests (`AgentReviewAuthorizationTest`, 5).** A patient token gets 403; a second doctor with no relationship to patient 100 sees an empty queue and gets 403 on claim; the reviewing doctor sees the run with its draft, `BILLING` intent and the billing citation, claims it, may re-claim, and the other doctor is refused; a staff reply removes the thread from the queue; and a dispatch that fails (503) leaves the message in the queue with `draft: null` and `RUN_PENDING` — the AI failure does not take the question out of human hands.

**Verification (actually run).** `mvn clean verify` → **360 tests, 0 failures, 1 skipped**; the opt-in dual-process test passes (11.0 s) with the new result payload; Python `pytest` → **204 passed**, `ruff` and `mypy --strict` clean (33 source files), `evals/run.py` → 156/156.

**Limits.**
- The queue shows one item per thread (the last patient message); a thread with several unanswered messages shows only the latest. 57.2's approve flow and 57.3's freshness checks are what make that safe.
- The claim is a row, not a lease: a claim held by an absent reviewer is not reclaimed yet (57.3's recovery work).
- Approve/edit/reject and publication are 57.2; nothing in this slice writes to the patient's thread.

### Round 57.1 review fixes (2026-10-28)

**1. P1 — the completion and the result were not one transaction.** The endpoint completed the run,
then saved the result in another transaction; a failure in between left `WAITING_REVIEW` with no
result, a capability that no longer verifies (the state is not `RUNNING`) and a reconcile sweep that
ignores it. `AgentReviewService.acceptResult` now performs the transition, the result write and the
capability revocation in **one** short transaction. Test: a spy makes the result save throw; the
endpoint answers 5xx, the run is still `RUNNING` with no result row, and the same capability can
complete it afterwards.

**2. P2 — ADMIN saw no threads it was not part of.** The queue enumerated every patient thread but
then queried messages for the *reviewer's* id, so an admin who never spoke to the patient got
nothing; claiming such a run also failed. Items are now built from the conversation's actual parties
(staff and patient) and the reviewer id is used only for the claim flags; ADMIN detection goes
through `DoctorPatientScope.resolve() == null`, the same rule the scope helper uses (the token's
scopes and `ROLE_*` authorities differ, which is what made the first attempt silently treat an admin
as a doctor). Test: an admin sees and claims a thread it is not part of.

**3. P2 — a concurrent first claim returned 500.** The read-then-insert raced, and the unique key
rejected the loser with an unhandled `DataIntegrityViolationException`. The insert now runs in its
own `REQUIRES_NEW` transaction (`AgentReviewClaimWriter`); the loser catches the violation and reads
the winner's row: same reviewer → idempotent success, another reviewer → 409. Tests: two concurrent
claims by the same reviewer both succeed with one row; two reviewers racing produce one winner and
one refusal.

**4. P2 — the body's `run_id` was not bound to the path.** A capability for run A could submit a
result claiming to belong to B and it was stored under A. The endpoint now requires
`request.runId == runId` (400 otherwise). Test: path A with body B is refused and neither the state
nor the result table changes.

**5. P2 — the shared schema tests did not exercise the Java DTO's own validation.** The fixture test
validates JSON against the schema with a hand-written validator; it says nothing about the Bean
Validation on the real endpoint. `citations` (null vs empty), its elements, `heading_path` (null,
blank elements), the required `requires_clinician` and `validation.ok` were tightened (boxed types so
a missing boolean cannot default to false), and the test now posts each invalid body to the real
endpoint: every one answers 400 and leaves the run `RUNNING` with no result; an empty citation list
is accepted for a routing summary.

**Verification (actually run).** `mvn clean verify` → **367 tests, 0 failures, 1 skipped**; the
opt-in dual-process test passes (11.0 s); Python `pytest` → 204 passed, `ruff`/`mypy --strict` clean,
`evals/run.py` 156/156.

## Round 58 — Read-only appointment tools (Pending)

**Goal:** authorized business retrieval without patient-record exposure. **Estimate:** 24–40 hours. Depends on Round 57 and the Round 55 authorization contract.

| Slice | Priority / status | Work | Tests first / acceptance |
|---|---|---|---|
| 58.1 Minimal appointment reads | P0 / Pending | Appointment-owned DTO/service/controller; task-derived patient; bounded upcoming query; clinic timezone contract | AgentAppointmentToolIntegrationTest: only own appointments; no chiefComplaint/notes/ICD payload; explicit time semantics and ownership on ID lookup |
| 58.2 Tool selection/execution | P1 / Pending | Tool registry and strict schemas; maximum calls/deadline; failure to human route | test_tool_execution.py: unknown tool denied; invalid args never execute; instructions inside tool results cannot grant access |
| 58.3 Isolation/recovery | P0 / Pending | Cross-patient/run/old-generation/capability tests and timeout/restart flows | AgentToolIsolationTest: all enumerated unauthorized cases rejected; resumed state never changes patient; no stale worker result accepted |

Return only necessary appointment ID/status/time/timezone and provider/department display fields. GetMessageStatus is optional later and must be side-effect-free. GetProviderAvailability and CreateStaffFollowUpRequest remain deferred; deterministic creation of a safety review is distinct from allowing a model-requested write.

## Round 59 — Evaluation, observability and release evidence (Pending)

**Goal:** reproducible evidence and honest operational boundaries. **Estimate:** 32–48 hours, excluding optional FAQ auto-response. Depends on completed earlier slices; foundational safety/testing/telemetry already exist.

| Slice | Priority / status | Work | Completion gate |
|---|---|---|---|
| 59.1 Golden dataset/regression | P1 / Pending | Expand to approximately 300–500 synthetic cases; classification/retrieval/clinical escalation/tools/attacks; retain held-out split | Every run identifies dataset/model/prompt/KB/commit; per-class metrics, error examples and denominators available |
| 59.2 Tracing/cost | P1 / Pending | Java/Python OTel correlation, graph/tool/retrieval spans, Langfuse, token/cost; export-stage masking | A run's path is traceable; sensitive canaries absent from logs/exported spans including third-party instrumentation; estimated cost uses recorded pricing assumptions |
| 59.3 Fault/database verification | P1 / Pending | Actual MySQL/PostgreSQL integration, timeout/429/network/kill/restart; backup/restore; retention tests | No lost accepted messages, duplicate publication or stale-worker writes in tested scenarios; raw evidence and limits recorded |
| 59.4 Portfolio/demo | P1 / Pending | Architecture/workflow/threat model, setup, benchmarks, trade-offs, demo script and truthful interview material | Clean environment starts; figures reproduce from raw output; single-instance and synthetic-data boundaries explicit |
| 59.5 Optional FAQ auto-response | P2 / Pending | Default-off allowlist of low-risk administrative FAQ; evidence/version/freshness check before publish; kill switch | Only after agreed eval gates; clinical/uncertain/error paths remain human; disabling switch preserves manual messaging |

### Evaluation and release criteria

All thresholds below are **candidate engineering gates, not measured results or clinical safety guarantees**. Confirm them before enabling 59.5.

| Dimension | Measurement / candidate gate |
|---|---|
| Intent | Accuracy, macro-F1 and per-class recall; candidate macro-F1 ≥0.85 |
| Safety escalation | Recall and false-positive rate on held-out high-risk cases; all designated critical blocking scenarios must pass; report sample counts and uncertainty |
| Retrieval | Evidence Recall@5, candidate ≥0.90; compare lexical, dense and hybrid |
| Groundedness | Human claim-level assessment, candidate ≥95%; any critical fabricated policy blocks automatic release |
| Tools | Selection and argument correctness separately, candidate ≥95%; deterministic ownership/schema gates cannot be bypassed regardless of average quality |
| Unauthorized access | Zero successful unauthorized requests in the enumerated suite; report its scope, never claim universally zero risk |
| Completion | Per-route definition; correct human escalation can be success; merely generating text is not success |
| Latency/cost | Fixed environment/model/concurrency; P50/P95, failures, retries, tokens and estimated cost per task |
| Staff value | Draft acceptance, substantive edit rate and review time; measure a real comparison before claiming improvement |

Use Mock LLM for deterministic PR tests. Real-model eval is separately triggered and versioned with a cost budget. pytest is the baseline; choose at most one of Ragas/DeepEval if it solves a measured need. LLM-as-a-Judge is auxiliary and must be checked against human labels for agreement and systematic mistakes. It must not decide whether access control, idempotency, token leakage or critical safety routing passed.

### Security and observability acceptance

- Re-authorize on tool calls, review and publication, including account disablement during execution.
- Prompt/document/tool-output injection cannot select arbitrary URLs/tools, change patient identity or request secret disclosure.
- Logs/traces default to opaque run correlation IDs, versions, node status, timings, bounded retrieval diagnostics and usage; no unrestricted input/output capture.
- Include checkpoint payloads, backups and evaluation exports in retention and encryption decisions; demonstrate cleanup of synthetic sensitive canaries.
- Preserve fail-safe human access even when AI, model provider, retrieval or tracing fails.
- Existing audit locking is valid for the initial single JVM. Database-level audit serialization and multi-instance SSE are P2 prerequisites for a later horizontally scaled deployment, not unfinished Round 53 fixes.
- No real PHI to DeepSeek or another external service in this scope. Any future real-data deployment separately reviews applicable provider terms, data processing/BAA requirements, retention, access and clinical oversight.

### Execution order, documentation and completion rules

1. Record a clean source SHA and fresh baseline results. Stop any instance using target/classes before clean builds, as CLAUDE.md requires.
2. Implement 54.1 → 54.2 → 54.3 → 54.4, then 55.1–55.4.
3. Implement 56.1–56.4 with the initial dataset; finish 57.1–57.3 for the first complete demo.
4. Implement 58.1–58.3; expand 59.1–59.4. 59.5 remains optional and blocked on evaluated release gates.
5. For each slice: specify behavior and schema; write the named meaningful failing test; record the red state; implement narrowly; run targeted and required regression checks; record actual outputs and limitations; only then mark Done.
6. Update this Roadmap per slice, API-LAYOUT for changed APIs, and schema/README instructions for migrations. Keep historical results intact and distinguish source-inspected evidence from tests actually run.
7. Preserve LayeringGuardTest without new allowlists. Do not broaden into unrelated refactoring or add frameworks merely for portfolio keywords.
8. Backend gate: mvn clean verify; frontend gate: npm run check and npm run build where affected; Python: locked install, lint/type checks and pytest. Add real MySQL/PostgreSQL coverage for migration/lease/concurrency behavior that H2 cannot establish.
9. Final artifacts: architecture and workflow diagrams, threat model, evaluated report with raw evidence, benchmark configuration, engineering trade-offs, reproducible README/demo and truthful resume/interview material. No invented accuracy, savings or performance gains.

Estimated focused implementation total: **176–276 hours**. With Python refresh and rework reserve, budget **210–330 hours**. These estimates exclude real clinical rollout, compliance certification, external clinical review and optional optimization.

**Remaining P2:** reranker only after retrieval evaluation; calibrated confidence only with adequate labeled data; real provider availability policy; model-requested write tools; multi-instance notification/audit; additional providers; Azure deployment. None blocks the draft/review/read-only-tool portfolio milestone.
