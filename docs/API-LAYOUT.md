# Medical Management System — Backend Layout & API Reference

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.1 |
| ORM | Spring Data JPA (Hibernate 6.x) |
| Database | MySQL 8.0 / H2 (dev) |
| Cache | Redis 7 (Redisson + Spring Cache) |
| Auth | Spring Boot OAuth2 Resource Server — issuer-routed: Okta JWKS (staff) + local HS256 (patient/emergency/refresh) |
| FHIR | HAPI FHIR R4 7.x |
| API Doc | Springdoc OpenAPI 2.7.0 |
| Util | Lombok |

## Directory Structure

```
src/main/java/com/example/medical/
├── MedicalApplication.java
├── common/
│   ├── audit/           AuditLogAspect, @Auditable annotation, AuditLog entity+repository
│   ├── base/            BaseEntity (id, createTime, updateTime, isDeleted), PageQuery, Pages (page bounds)
│   ├── config/          SecurityConfig, CacheConfig, JpaConfig,
│   │                    AesAttributeConverter, RateLimiterConfig, DataInitializer
│   ├── enums/           ResultCode (200, 400, 401, 403, 404, 409, 500)
│   ├── exception/       BusinessException, GlobalExceptionHandler (@RestControllerAdvice)
│   └── result/          Result<T>, PageResult<T> (total, size, current, records)
├── module/
│   ├── system/          users, roles, menus, auth (login/refresh/logout)
│   ├── patient/         patient CRUD, patient portal, FHIR case export, patient auth
│   ├── appointment/     appointment scheduling with conflict detection
│   ├── prescription/    prescriptions + items (CRUD) + CDS + ePrescribing
│   ├── billing/         bills + payment (claim lifecycle)
│   ├── chat/            patient-doctor messaging
│   ├── dashboard/       aggregate stats (JdbcTemplate)
│   ├── export/          CSV export (patients, bills)
│   ├── integration/     ADT + lab results JSON API
│   └── quality/         eCQM clinical quality measures
├── security/            JwtClaimMapper, LoginUser, DevJwtEncoder
```

## Response Envelope

All endpoints return `Result<T>`:

```json
{ "code": 200, "message": "ok", "data": { ... } }
```

Paginated endpoints return `Result<PageResult<T>>`:

```json
{ "code": 200, "message": "ok", "data": { "total": 100, "size": 10, "current": 1, "records": [...] } }
```

All responses are DTO/VO objects (never raw entities) — conversion lives in the DTO classes (`fromEntity()`).

---

## API Endpoints

### Auth — `/api/v1/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/login` | public | Staff login — dev: BCrypt + local JWT, prod: Okta password grant |
| POST | `/refresh` | Bearer token | Refresh staff access token via IdP (not available in dev mode) |
| POST | `/logout` | Bearer token | Invalidate session at IdP / notify client to discard token |

### Patient Auth — `/api/v1/patient`

Patient accounts are self-managed (local `patient_auth` table, BCrypt + local JWT). No external IdP involvement.
Tokens are long-lived (default 24h, configurable via `app.security.patient-token-expiry-seconds`);
expired tokens require re-login.

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/login` | public | Patient login — local BCrypt verification, returns locally-signed JWT + refresh token |
| POST | `/refresh` | public | Patient token refresh — validate refresh JWT (scp=refresh, role=PATIENT), returns new access+refresh token pair |

### User Management — `/api/v1/users`

All require `ADMIN` role.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10&keyword=` | Paginated user list |
| GET | `/{id}` | path | User detail (cached) |
| POST | `/` | body: SysUserFormDTO | Create user |
| PUT | `/{id}` | path + body | Update user (evicts cache) |
| PUT | `/{id}/unlock` | path | Unlock a locked account (clears failed attempts + lock expiry) |
| DELETE | `/{id}` | path | Soft-delete user (evicts cache) |
| GET | `/doctors` | — | Doctor list `DoctorOptionVO[]` `{id, username, realName}` (ADMIN,DOCTOR — used by appointment/prescription forms) |

### User Profile — `/api/v1/users/me`

Any authenticated user.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | @AuthenticationPrincipal | Current user profile |
| PUT | `/` | body: {realName, phone, email, gender} | Update profile |
| PUT | `/password` | body: {oldPassword, newPassword} | Change password |

### Role Management — `/api/v1/roles`

All require `ADMIN` role.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10&keyword=` | Paginated role list |
| POST | `/` | body: SysRoleFormDTO | Create role |
| PUT | `/{id}` | path + body | Update role |
| DELETE | `/{id}` | path | Soft-delete role |

### Menu Management — `/api/v1/menus`

All require `ADMIN` role.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/tree` | — | Menu tree (hierarchical) |
| GET | `/` | — | Flat menu list |
| POST | `/` | body: SysMenuFormDTO | Create menu |
| PUT | `/{id}` | path + body | Update menu |
| DELETE | `/{id}` | path | Soft-delete menu |

### Patient Management — `/api/v1/patients`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&keyword=` | Paginated patient list |
| GET | `/{id}` | ADMIN,DOCTOR | path | Patient detail (cached) |
| GET | `/{id}/case` | ADMIN,DOCTOR | path | FHIR R4 Bundle (Patient + Conditions + Encounters + Medications + Allergies) |
| POST | `/` | ADMIN,DOCTOR | body: PatientFormDTO | Create patient |
| PUT | `/{id}` | ADMIN,DOCTOR | path + body | Update patient (evicts cache) |
| GET | `/{patientId}/history` | ADMIN,DOCTOR | path | List medical history entries (append-only, ordered by date descending) |
| POST | `/{patientId}/history` | ADMIN,DOCTOR | body: {description} | Add medical history entry (recordedBy captured from auth) |
| GET | `/{patientId}/allergies` | ADMIN,DOCTOR | path | List allergy entries (append-only) |
| POST | `/{patientId}/allergies` | ADMIN,DOCTOR | body: {allergen, reaction?, severity?} | Add allergy entry |
| PUT | `/{patientId}/allergies/{id}/resolve` | ADMIN,DOCTOR | path | Resolve allergy entry (status → resolved, records resolvedBy + resolvedAt). Rejects if already resolved (409) |
| DELETE | `/{id}` | ADMIN | path | Soft-delete patient |

### Patient Portal — `/api/v1/patient/me`

All require `PATIENT` role.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | — | Current patient profile |
| PUT | `/` | body: `PatientSelfUpdateFormDTO` (12 fields) | Self-service profile update — full update, `@Size` bounded, 400 on an over-long field. Staff-verified fields are not part of the body at all — see the section below |
| GET | `/appointments` | `?page=1&size=10` | My appointments |
| PUT | `/appointments/{id}/cancel` | path | Cancel own appointment (status → 2). Rejects an appointment that is already cancelled/completed (409) and one whose time has passed (409) |
| GET | `/prescriptions` | `?page=1&size=10` | My prescriptions |
| GET | `/bills` | `?page=1&size=10` | My bills |
| GET | `/export` | — | HIPAA Right of Access — full data export (demographics + appointments + prescriptions + bills) |
| GET | `/observations` | `?loinc=&page=1&size=20` | My lab results — `PageResult<ObservationVO>`; `loinc` filters server-side |
| GET | `/observations/trend` | `?loinc=` (required) | Full history of one test (bounded single-test dataset) for trend rendering |
| GET | `/vitals` | — | My vital signs |
| GET | `/problems` | — | My problem list |
| GET | `/immunizations` | — | My immunizations |
| GET | `/care-plans` | — | My care plans |
| GET | `/referrals` | — | My referrals |
| GET | `/prior-auths` | — | My prior authorizations |
| GET | `/disclosures` | `?page=1&size=20` | HIPAA §164.528 accounting of disclosures — audit_log rows for my patientId |
| GET | `/consent` | — | My consent records |
| GET | `/refill-requests` | — | My prescription refill requests |
| POST | `/refill-requests` | body: {prescriptionId, reason?} | Request a refill for a prescription |
| GET | `/messages/conversations` | `?page=1&size=20` | My chat conversations |
| GET | `/messages/{partnerId}` | `?page=1&size=50` | Chat messages with a staff member |
| POST | `/messages` | body: {receiverId, content} | Send a chat message |
| PUT | `/bills/{id}/pay` | body: `PatientPayBillFormDTO` {paymentAmount (> 0, required), paymentMethod} | Pay own bill (PENDING → PAID). Ownership verified (403 for someone else's bill). DRAFT is not payable; a missing amount is 400, not 500 |
| PUT | `/password` | body: `PatientPasswordChangeFormDTO` {oldPassword, newPassword} | Change password — verifies the old one (400), enforces complexity (`@ValidPassword`) and the last-3 history policy |
| POST | `/patient/forgot-password` | body: {username} | Public. Issues a 30-min single-use reset token (logged to console in dev; identical response for unknown users — no enumeration) |
| POST | `/patient/reset-password` | body: {token, newPassword} | Public. Resets password (policy-enforced), clears lockout; token single-use, 401 on invalid/expired/reused |

### Appointments — `/api/v1/appointments`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&status=&patientId=` | Paginated list (DOCTOR scoped to own patients) |
| GET | `/conflicts` | ADMIN,DOCTOR | `?doctorId=&time=&excludeId=` | Appointments overlapping the doctor's 30-min window (excludes cancelled; excludeId = self when editing) |
| GET | `/{id}` | ADMIN,DOCTOR | path | Appointment detail |
| POST | `/` | ADMIN,DOCTOR | body: AppointmentFormDTO | Create (30-min conflict check) |
| PUT | `/{id}` | ADMIN,DOCTOR | path + body | Update (30-min conflict check) |
| DELETE | `/{id}` | ADMIN | path | Soft-delete |

Appointment statuses: 0 = Scheduled, 1 = Arrived, 2 = Cancelled, 3 = Completed, 4 = No-Show, 5 = Rescheduled, 6 = In Progress. Statuses 2/3/4 are terminal — update rejected with 409.

### Prescriptions — `/api/v1/prescriptions`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&patientId=` | Paginated list (DOCTOR scoped to own patients) |
| GET | `/{id}` | ADMIN,DOCTOR | path | Prescription detail with items |
| GET | `/by-patient/{patientId}` | ADMIN,DOCTOR | path | All prescriptions for a patient (used by emergency break-glass) |
| POST | `/` | ADMIN,DOCTOR | body: PrescriptionFormDTO (+optional `overrideReason`) | Create + items. CDS (drug-drug, active-medication, allergy incl. cross-reactive) runs BEFORE save; severe/contraindicated warnings block (409) unless `overrideReason` is provided (persisted to cds_override). Prescriber identity (doctorId/NPI/DEA) is server-derived from the authenticated user |
| DELETE | `/{id}` | ADMIN | path | Soft-delete + items (hidden for transmitted/dispensed/cancelled) |
| PUT | `/{id}/transmit` | ADMIN,DOCTOR | `?pharmacyId=` | Generate draft NCPDP XML for active prescriptions (non-controlled only). Returns `TransmitResultVO` `{status:"generated", format, messageId, xml}` — controlled substances are rejected (409, EPCS fail-closed) and nothing is ever marked "transmitted" (Review III C4) |
| PUT | `/{id}/cancel` | ADMIN,DOCTOR | path | Cancel prescription (active→cancelled). Rejects non-active (409). Prescriptions are cancel-reissue — no in-place edit endpoint (Round 28/34) |

### Prescription Refill Requests — `/api/v1/prescriptions/refill-requests`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | ADMIN,DOCTOR | Pending refill requests (patient → doctor approval workflow) |
| PUT | `/{id}/approve` | ADMIN,DOCTOR | Approve a refill request — consumes one refill from the prescription items (409 when none remain). POST create requires the prescription to belong to the patient, be active, and have no pending duplicate (Review III C8) |
| PUT | `/{id}/deny` | ADMIN,DOCTOR | body: {notes?} — deny a refill request |

### Billing — `/api/v1/bills`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&patientId=` | Paginated list (DOCTOR scoped to own patients) |
| GET | `/{id}` | ADMIN,DOCTOR | path | Bill detail |
| POST | `/` | ADMIN,DOCTOR | body: BillFormDTO | Create bill (DRAFT) |
| PUT | `/{id}/submit` | ADMIN,DOCTOR | path | Submit claim (DRAFT → SUBMITTED) |
| PUT | `/{id}/adjudicate` | ADMIN | body: {insurancePayment, adjustment, claimNumber, adjudicationDate} | Adjudicate (SUBMITTED/PENDING). Rejects PAID/DENIED (409) |
| PUT | `/{id}/pay` | ADMIN | body: {paymentAmount, paymentMethod} | Staff-side payment (PENDING → PAID). DRAFT not payable. Rejects PAID/DENIED (409) |
| PUT | `/{id}/deny` | ADMIN | body: {reason} | Deny claim (PENDING → DENIED). Rejects PAID (409) |
| DELETE | `/{id}` | ADMIN | path | Soft-delete |

Claim lifecycle: DRAFT → SUBMITTED → (adjudicate) → PENDING → (pay) PAID / (deny) DENIED.

### Charges (Superbill) — `/api/v1/charges`

Requires `ADMIN` or `DOCTOR`. Charge capture linked to appointments, convertible to bills.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10&patientId=` | Paginated charge list (`ChargeVO`; DOCTOR scoped to own patients) |
| POST | `/` | body: {patientId*, chargeAmount*, appointmentId?, cptCodes?, icd10Codes?, units?, visitType?, notes?} | Create charge (DRAFT). `patientId` and `chargeAmount` required (400 otherwise; amount must be ≥ 0) |
| PUT | `/{id}/convert` | path | Convert DRAFT charge → bill (`BillVO`; status → BILLED, creates Bill, copies `appointmentId` onto it). Non-DRAFT → 409; an appointment that **already has any bill** → 409 `Appointment 201 is already billed (bill 500)` — a visit is billed once (Round 51.1) |

### Referrals — `/api/v1/referrals`

Requires `ADMIN` or `DOCTOR`. Referral lifecycle: PENDING → SCHEDULED → COMPLETED → CLOSED (status transitions via update).

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10&patientId=` | Paginated referral list |
| GET | `/patients/{patientId}/referrals` | path | All referrals for a patient |
| POST | `/` | body: ReferralForm | Create referral. `referringDoctorId` optional — defaults to the authenticated doctor |
| PUT | `/{id}` | path + body | Update status/appointmentDate/completionDate/notes (Schedule/Complete/Close) |

### Problems (Problem List) — `/api/v1/patients/{patientId}/problems`

Requires `ADMIN` or `DOCTOR`. SNOMED CT + ICD-10 coded problem list. Status: ACTIVE / RESOLVED.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10` | Paginated problems for a patient |
| POST | `/` | body: {snomedCode?, snomedDisplay, icd10Code?, onsetDate?, severity?, notes?} | Add problem (default status ACTIVE) |
| PUT | `/{id}` | path + body | Resolve (`status=RESOLVED` + resolutionDate) or update severity/notes |

### Vital Signs — `/api/v1/patients/{patientId}/vitals`

Requires `ADMIN` or `DOCTOR`. BP/HR/temp/RR/O₂/BMI.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10` | Paginated vital signs for a patient |
| POST | `/` | body: {systolicBp?, diastolicBp?, heartRate?, temperature?, respiratoryRate?, oxygenSaturation?, heightCm?, weightKg?, bmi?, notes?} | Record vital signs |

### Immunizations — `/api/v1/patients/{patientId}/immunizations`

Requires `ADMIN` or `DOCTOR`. CVX-coded immunizations.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10` | Paginated immunizations for a patient |
| POST | `/` | body: {vaccineName, cvxCode?, administrationDate?, lotNumber?, manufacturer?, doseNumber?, site?, route?, notes?} | Record immunization (default status `completed`) |

### Care Plans — `/api/v1/patients/{patientId}/care-plans`

Requires `ADMIN` or `DOCTOR`. Status: ACTIVE / COMPLETED.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10` | Paginated care plans for a patient |
| POST | `/` | body: {title, goal?, interventions?, startDate?, targetDate?, notes?} | Create care plan (default status ACTIVE) |
| PUT | `/{id}` | path + body | Update status/completedDate/notes |

### Prior Authorizations — `/api/v1/prior-auths`

Requires `ADMIN` or `DOCTOR`. Status: PENDING → APPROVED / DENIED.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10&patientId=` | Paginated prior auth list |
| POST | `/` | body: {patientId, authType, itemName?, itemCode?, insurancePayer?, notes?} | Create prior auth (default status PENDING) |
| PUT | `/{id}` | path + body | Approve (`status=APPROVED` + authNumber + resolvedAt) or deny (`status=DENIED` + resolvedAt) |

### Formulary — `/api/v1/formulary`

Requires `ADMIN` or `DOCTOR`. Drug formulary coverage lookup.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/check` | `?rxnormCode=&insurancePayer=` | Coverage check — `FormularyCheckVO`: a hit carries `{found, drugName, tier, priorAuthRequired, stepTherapyRequired, alternatives}`, a miss only `{found: false, message}` (no caller in the UI today) |
| GET | `/{rxnormCode}` | path | All formulary entries for a drug across payers |

### Chat (Staff) — `/api/v1/messages`

Requires `ADMIN` or `DOCTOR`. Message parties are typed (`STAFF` = sys_user id, `PATIENT` = patient id) because the two ID spaces overlap (Round 44 fix R2-1).

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/conversations` | `?page=1&size=20` | Paginated conversation list — each record carries `partnerType` (`STAFF`/`PATIENT`) |
| GET | `/unread-count` | — | Total unread message count (sidebar badge) |
| GET | `/{partnerId}` | `?partnerType=STAFF\|PATIENT&page=1&size=50` | Paginated messages with partner (auto-read). `partnerType` required, invalid value → 400 |
| POST | `/` | body: {receiverId, receiverType, content} | Send a message. `receiverType` required (`STAFF`/`PATIENT`), missing/invalid → 400 |

`MessageVO` carries `senderType`/`receiverType`; `ConversationVO` carries `partnerType`.

### Chat SSE Ticket — `POST /api/v1/chat/sse-ticket`

Requires `ADMIN`/`DOCTOR`/`PATIENT` (Bearer JWT via header). Returns `{ "ticket": "<random>", "expiresIn": 30 }` — a single-use, 30-second ticket bound to the caller's `(type, userId)`. The JWT never appears in a URL.

### Chat SSE — `GET /api/v1/chat/subscribe?ticket=<ticket>`

Server-Sent Events endpoint for real-time message push. `permitAll` in the security chain — authentication is the single-use ticket obtained from `POST /api/v1/chat/sse-ticket` (EventSource cannot send Authorization headers). Invalid/expired/reused ticket → 401. Returns `text/event-stream` with `new_message` events. Emitters are keyed `type:id` so a patient and a staff user with the same numeric id cannot overwrite each other's connection.

### Chat (Patient) — `/api/v1/patient/me/messages`

Requires `PATIENT`. Same endpoints as staff chat (including `GET /unread-count`), except: `GET /{partnerId}` and `POST /` need no `partnerType`/`receiverType` — partners are always `STAFF` (patients only chat with staff).

### Dashboard — `/api/v1/dashboard`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/stats` | Aggregate stats (cached 30 min): `totalPatients`, `todayAppointments`, `scheduledAppointments`, `monthlyRevenue`, `monthlyPrescriptions`, `pendingBills`, `appointmentStatusDistribution`, `revenueTrend` |

**The counters follow the same doctor scoping as the lists** — a `DOCTOR`'s `totalPatients` is the size of `/api/v1/patients` for that same token, `pendingBills` matches `/api/v1/bills?claimStatus=PENDING`, `scheduledAppointments` matches `/api/v1/appointments?status=0`; `ADMIN` sees the whole practice. These are raw `JdbcTemplate` queries, so unlike the JPA-backed lists they do **not** inherit `@SQLRestriction` or scoping automatically — both are applied by hand in `DashboardService`, and `DashboardIntegrationTest` asserts the agreement for both roles (before Round 51.7 a doctor was told "Total Patients 4" while their own list held 2).

### Export — `/api/v1/export`

Requires `ADMIN` or `DOCTOR`. DOCTOR role is scoped to own patients only (from appointments/prescriptions); ADMIN exports all records. The scope is applied in the query, so a scoped file contains exactly the rows the same caller can list (Round 51.9); an empty scope yields a header-only file.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/patients` | CSV download (DOCTOR: own patients; ADMIN: all). PHI masked: phone→last4, email→j***@domain |
| GET | `/bills` | CSV download (DOCTOR: own patients' bills; ADMIN: all). Claim numbers masked |

### Audit Logs — `/api/v1/audit-logs`

Requires `ADMIN`. HIPAA §164.312(b) compliance.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=20&userId=&patientId=&module=&action=&fromDate=&toDate=` | Search/filter audit logs |
| GET | `/distinct-values` | — | Distinct module/action values for filter dropdowns |
| GET | `/verify` | — | Tamper-evidence check: verifies row_hash chain, returns `IntegrityReportVO` `{intact, brokenRowId}` (Review III M2) |

### FHIR — `/api/v1/fhir`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/metadata` | public | CapabilityStatement (FHIR 4.0.1 + SMART on FHIR security) |
| GET | `/Patient/{id}` | ADMIN,DOCTOR | FHIR Patient resource (SSN masked to last-4); DOCTOR limited to own patient scope (403 otherwise, emergency token exempt) |
| GET | `/Patient` | ADMIN,DOCTOR | FHIR search (`?_id=100`) returning Bundle; DOCTOR: `_id` outside scope → 403, no `_id` → results filtered to scope |

### Consent — `/api/v1/consent`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?patientId=` | List consent records for a patient |
| POST | `/` | body: {patientId, consentType, scope} | Create consent record |
| PUT | `/{id}/revoke` | path | Revoke a consent |

### Emergency Access — `/api/v1/emergency`

Requires `ADMIN` or `DOCTOR`. Break-glass access with mandatory audit.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| POST | `/access/{patientId}` | body: {reason} | Returns `EmergencyAccessTokenVO` {token, expiresInMinutes, patientId} — short-lived (30min) JWT with `scope=EMERGENCY` + `patientId` claim. Use this token to access the specific patient's data via `/patients/{id}` |
| GET | `/history` | `?patientId=&audited=0` | View emergency access history — `EmergencyAccessVO[]` (filter by patient or unreviewed; ADMIN only) |
| PUT | `/{id}/review` | path | Mark emergency access as reviewed — sets `audited=1`, `reviewedBy`, `reviewedAt` (ADMIN only) |

### Key Management — `/api/v1/admin/keys`

Requires `ADMIN`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/history` | Key lifecycle audit trail (KEY_INIT / KEY_ROTATION events) |
| POST | `/rotate` | Trigger runtime key rotation — body: {newKey, oldKey}. Records the new key fingerprint in key_audit; **must** be followed by updating AES_KEY (new) and AES_KEY_PREVIOUS (old) in env before restart (Review III C2) |
| GET | `/rotation-status` | Key rotation migration progress — per-table migrated row counts |

### CDS — `/api/v1/cds`

Requires `ADMIN` or `DOCTOR`. Clinical Decision Support — pre-prescription screening.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| POST | `/check` | body: {patientId, items[{rxnormCode, drugName}]} | Check drug-drug interactions + drug-allergy contraindications before prescribing. Returns `{passed, warnings[]}` |
| GET | `/drugs` | `?rxnorm=` | Drug name lookup by RxNorm code (unknown code → empty drugName) |

### Integration — `/api/v1/integration`

Requires `ADMIN` or `DOCTOR`. Mirth Connect JSON integration for ADT and lab results.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| POST | `/adt` | body: AdtEventDTO, header `X-Integration-Key` | ADT event (A01/A03/A08) — upsert Patient by MRN. Returns `IntegrationAckVO` `{status:"ACK", sourceMessageId}` |
| POST | `/lab-results` | body: LabResultDTO, header `X-Integration-Key` | Returns `IntegrationAckVO` `{status, sourceMessageId, recordsCreated}`. Batch lab results with sourceMessageId dedup. `results[].abnormalFlag` accepts the HL7 set and is **normalised to one character** before storage (`HH/HU → H`, `LL/LU → L`, `N`, `A`; anything else → null) because the column is `CHAR(1)` (Round 51.4) |

### Lab Results — `/api/v1/patients/{id}/observations` + `/api/v1/loinc`

Requires `ADMIN` or `DOCTOR` (catalog also `PATIENT`). Lab trend analysis and LOINC catalog.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/patients/{id}/observations` | `?loinc=&page=1&size=20` | Paginated lab results (`PageResult<ObservationVO>`); `loinc` filters server-side |
| GET | `/patients/{id}/observations/trend` | `?loinc=` (required) | Full history of one test for trend rendering |
| GET | `/loinc/catalog` | — | Full LOINC dictionary (ADMIN,DOCTOR,PATIENT) |
| GET | `/loinc/panel/{parentCode}` | path | LOINC codes grouped by panel (CBC/BMP/LIPID) |

### FHIR Observation — `/api/v1/fhir/Observation`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/Observation/{id}` | ADMIN,DOCTOR | Single FHIR Observation resource; DOCTOR limited to own patient scope (403 otherwise) |
| GET | `/Observation?patient=` | ADMIN,DOCTOR | Observations by patient (Bundle); DOCTOR: patient outside scope → 403; no `patient` param → results filtered to scope |

### Pharmacy — `/api/v1/pharmacies`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?zip=&state=` | Search pharmacy directory |

### ePrescribing — `/api/v1/prescriptions/{id}/transmit`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| PUT | `/{id}/transmit` | `?pharmacyId=` | Transmit prescription via NCPDP SCRIPT, EPCS audit if controlled substance. Returns `TransmitResultVO` `{status, format, messageId, xml}` |

### eCQM — `/api/v1/quality`

Requires `ADMIN` or `DOCTOR`. CMS MIPS/MACRA clinical quality measures.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/measures` | — | List all quality measure definitions |
| GET | `/measures/{cmsId}/report` | path | Latest persisted performance report (CMS122/CMS125/CMS165) — `QualityReportVO` (10 keys) |
| POST | `/measures/{cmsId}/calculate` | path | Run the measure calculation now; result persisted to `quality_result`. Same `QualityReportVO` shape as the read-back — **`title` is the measure's own title in both** (it used to be the target description here, so one key meant two different things); the description lives in `performanceTarget` |
| GET | `/measures/{cmsId}/history` | path | Persisted calculation history for a measure |

---

## Security

### OAuth2 Flow
1. Client authenticates against external IdP (Okta / Auth0) and receives a JWT access token.
2. Client sends `Authorization: Bearer <token>` on all requests.
3. `OAuth2ResourceServer` config validates the JWT against the IdP's JWKS endpoint.
4. Custom `JwtAuthenticationConverter` maps IdP claims (roles, permissions, sub) to Spring Security `GrantedAuthority` and builds a `LoginUser` principal.
5. `@PreAuthorize("hasRole('ADMIN')")` / `hasAnyRole(...)` enforces access on controller methods.

### Role-Based Access

| Role | Scope |
|------|-------|
| ADMIN | Full access — all endpoints |
| DOCTOR | Patient/appointment/prescription/bill CRUD, dashboard, export, chat, own profile. **Clinical/billing data is scoped to own patients** (patients they have appointments or prescriptions with) — see note below. |
| PATIENT | Patient portal (`/api/v1/patient/me/*`), patient chat (`/api/v1/patient/me/messages/*`) |

### DOCTOR Patient Scoping (Round 47)

For `DOCTOR`, clinical/billing data endpoints are scoped to the doctor's own patients (defined as patients where the doctor has appointments or prescriptions — `DoctorPatientScope` in `common/security`, fed by a `DoctorPatientScopeProvider` per module so that the shared kernel never imports a module):

- **List endpoints** (`appointments`, `prescriptions`, `charges`, `referrals`, `prior-auths`, refill pending list) filter rows to in-scope patients; ADMIN sees all.
- **By-patient read/update endpoints** (vitals, observations + trend, problems, care-plans, immunizations, patient history/allergies, FHIR case, referral/prior-auth lists, appointment/prescription/bill detail, charge convert, refill approve/deny) return **403** for out-of-scope patients.
- **Create endpoints** (vitals, problems, care-plans, immunizations, patient history/allergies, referrals, prior-auths) return **403** when the target patient is out of scope.
- **The patient directory** (`GET /patients`, `GET /patients/{id}`, patient search dropdowns, patient creation) stays open — booking or prescribing for a new patient establishes the care relationship.
- **Emergency break-glass tokens** (`scope=EMERGENCY`, `patientId` claim) bypass the scope for the named patient.

### Data Encryption
- **Passwords**: BCrypt hashed + complexity policy (8+ chars, upper/lower/digit/special) + history enforcement (last 3 cannot be reused). The complexity rule applies to a password that is **supplied**: where the field is optional (`PUT /api/v1/users/{id}`), an absent or blank value keeps the current password rather than failing validation (M8.5) — required fields carry `@NotBlank` of their own
- **PHI fields**: AES-256-GCM via `@Convert(converter = AesAttributeConverter.class)`, versioned key format supporting rotation (`app.aes.key` / `app.aes.key.previous`)
- **Redis cache safety**: `PhiMaskingRedisSerializer` automatically redacts `@PhiField`-annotated DTO fields to `[PHI-REDACTED]`
- **Token validation**: External IdP JWKS; no local secret management needed

### Account Security
- System users: 5 failed logins → 15-minute lockout (matching patient lockout)
- Patients: 5 failed logins → 15-minute lockout
- Token expiry: configurable via `app.security.access-token-expiry-seconds` (default 7200s)
- Security headers: HSTS (1yr), X-Content-Type-Options, X-Frame-Options DENY, XSS Protection, Cache-Control

### Data Retention
- Audit logs: nightly **archival** (flag flip, not a delete) of rows older than `app.retention.audit-log-days` (default 2190 = 6 years), by `common/job/DataRetentionJob` at `app.retention.cron`
- Soft-deleted records: **no purge runs.** An earlier version of this document described a `app.retention.soft-delete-days` policy; the knob existed in `application.yml`, nothing read it, and it has been removed (M8.5). Physically removing a patient's soft-deleted clinical rows on a timer is a compliance decision, not a clean-up task, so it is left unimplemented rather than half-configured

## Infrastructure

### Cache (Redis)

| Cache | Key | TTL | Eviction |
|-------|-----|-----|----------|
| `patients` | `#id` | 30 min | on create → all; on update/delete → by id |
| `users` | `#id` | 30 min | same pattern |
| `dashboard` | `stats:<scope>` — `ALL` for ADMIN, the sorted patient-id list for a DOCTOR | 30 min | none |

> The dashboard key must carry the scope: it used to be the constant `'stats'`, which is harmless while `spring.cache.type: none` (the h2 profile) but under Redis handed the first caller's numbers to everyone else (Round 51.7).

### Audit Logging
AOP-based via `@Auditable(module, action)`. Captures userId, username, module, action, targetId, patientId, IP, timestamp → `audit_log` table. Applied to all CUD service operations, and to **reads that target a single patient**: opening a patient (`patient:VIEW`), their history/allergies (`VIEW_HISTORY`/`VIEW_ALLERGIES`), vitals/problems/immunizations/care-plans/referrals/consent/observations/prescriptions (`VIEW`), the FHIR patient and observation reads (`FHIR_VIEW`) and the FHIR case bundle. The row carries the `patientId`, so the portal's own access history (`GET /api/v1/patient/me/disclosures`) can show who opened the record. Unaudited by design: list/search endpoints with no single patient (patient search, FHIR search) and reference data (LOINC catalog). **21 CFR Part 11 compliant:** SHA-256 `row_hash` for tamper detection, soft-delete (`archived` flag) instead of physical deletion, login success/failure audited with reason codes.

### Patient self-service update — `PUT /api/v1/patient/me`

Body: `PatientSelfUpdateFormDTO` — exactly the twelve fields a patient may change:
`phoneMobile`, `phoneHome`, `phoneWork`, `email`, `addressLine1`, `addressLine2`,
`city`, `state`, `zipCode`, `emergencyContactName`, `emergencyContactPhone`,
`emergencyContactRelation`. Each carries a `@Size` bound derived from its encrypted
column (`VARCHAR(200)` fits 71 characters of plaintext, `VARCHAR(300)` fits 121;
`emergencyContactRelation` is not encrypted and is bounded by its `VARCHAR(50)`);
an over-long value returns **400** naming the field instead of a database error.

**Behaviour changes (M8.2):** the body used to be an untyped `Map`, and
staff-verified fields sent alongside it were silently ignored. They are now simply
not part of the contract — legal name, MRN, date of birth, sex at birth, insurance
and allergies cannot be changed from this endpoint at all (an unknown key is
ignored, so sending `name` changes nothing). The endpoint is also a true **full
update**: a field omitted from the body is cleared, where previously only the keys
present were touched. The portal submits all twelve.

### Error status contract

`Result<T>` carries the same code as the HTTP status. The mapping is deliberate, not incidental:

| Situation | Status |
|-----------|--------|
| Business rule rejected (`BusinessException`), bean validation, malformed JSON, a non-numeric path variable, a missing parameter | **400** |
| Not authenticated / token rejected | **401** (empty body, `WWW-Authenticate: Bearer`) |
| Authenticated but not permitted (`@PreAuthorize`) | **403** |
| Unknown path | **404** `No such endpoint` |
| Verb not allowed on a known path | **405** `Method not allowed` |
| Anything else | **500**, logged with a stack trace |

An unrecognised path or verb used to answer 500 because the catch-all handler swallowed Spring's `NoResourceFoundException`/`HttpRequestMethodNotSupportedException` — clients saw a server error for their own mistake (fixed in M6).

**The 403 row above was aspirational until M8.4.** `SecurityConfig` guards the URL tree with `authenticated()` only, so every role check in this application is a `@PreAuthorize` on a controller method — and its `AuthorizationDeniedException` was thrown *inside* the DispatcherServlet, where the catch-all turned it into `500 Internal server error` plus an ERROR stack trace. Measured before the fix: a patient token on `/api/v1/patients`, a doctor token on the ADMIN-only `/api/v1/audit-logs`, and a staff token on `/api/v1/patient/me` all answered 500. A dedicated handler now maps it to `403 Access denied` (body identical to a `BusinessException(FORBIDDEN)`, so the frontend cannot tell the two apart — which is the point).

### Pagination (all list endpoints)

`?page` is 1-based; `?size` is capped at **200** (`common/base/Pages.java`, also referenced by `PageQuery`). Out-of-range values are **rejected with 400**, never clamped — a client asking for 10 000 rows is told no rather than silently given 200. Two message shapes exist because two mechanisms enforce the same limit: raw `@RequestParam` endpoints answer `{"code":400,"message":"Size must be between 1 and 200"}`, `PageQuery`-bound endpoints answer `size: Size must be at most 200` (bean validation). FHIR endpoints use their own `_count` cap of 500, per the FHIR contract.

**Behaviour change (M7):** before this round the raw-parameter endpoints had no bound at all and `?size=9999` was served; the frontend used exactly that for CSV export and `size: 999` for a patient dropdown.

### Rate Limiting
Redisson `RRateLimiter` filter: 10 req/min/IP on login, 20 req/min/IP on token refresh, 5 req/hour/IP on CSV export. Returns HTTP 429.

### Encryption
AES-256-GCM via JPA `@Convert` — transparent at-rest encryption. PBKDF2-HMAC-SHA256 (310k iterations) key derivation. Versioned ciphertext for key rotation. Redis cache PHI automatically redacted by `PhiMaskingRedisSerializer` + `@PhiField`.

### Database Conventions
- All entities extend `BaseEntity`: `id` (auto-generated), `createTime`, `updateTime` (auto-managed via `@PrePersist`/`@PreUpdate`), `isDeleted` (logical delete via `@SQLDelete`)
- `@Version` optimistic locking on critical entities
- No physical deletes — `@SQLDelete(sql = "UPDATE {table} SET is_deleted = 1 WHERE id = ? AND version = ?")`
- `@SQLRestriction("is_deleted = 0")` on entity level for automatic soft-delete filtering (Hibernate 6.x)

### FHIR Interoperability
- HAPI FHIR R4 provides standard US healthcare data models.
- `GET /api/v1/patients/{id}/case` returns a FHIR Bundle containing Patient, Condition, AllergyIntolerance, Encounter, and MedicationRequest resources.
- All FHIR resources use standard coding systems (SNOMED CT, LOINC, RxNorm) where applicable.
