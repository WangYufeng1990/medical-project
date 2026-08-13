# Medical Management System — Backend Layout & API Reference

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4.1 |
| ORM | Spring Data JPA (Hibernate 6.x) + Querydsl |
| Database | MySQL 8.0 / H2 (dev) |
| Cache | Redis 7 (Redisson + Spring Cache) |
| Auth | Spring Boot OAuth2 Resource Server (external IdP) |
| FHIR | HAPI FHIR R4 7.x |
| API Doc | Springdoc OpenAPI 2.7.0 |
| Util | Lombok, Hutool 5.8.34 |

## Directory Structure

```
src/main/java/com/example/medical/
├── MedicalApplication.java
├── common/
│   ├── audit/           AuditLogAspect, @Auditable annotation, AuditLog entity+repository
│   ├── base/            BaseEntity (id, createTime, updateTime, isDeleted), PageQuery
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
├── security/            JwtClaimMapper, SecurityConfig
└── util/                CsvUtil
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
| GET | `/doctors` | — | Doctor list `[{id, username, realName}]` (ADMIN,DOCTOR — used by appointment/prescription forms) |

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
| PUT | `/` | body: {phoneMobile, email, addressLine1, city, state, zipCode, ...} | Self-service profile update (name/DOB/MRN blocked — requires staff verification) |
| GET | `/appointments` | `?page=1&size=10` | My appointments |
| PUT | `/appointments/{id}/cancel` | path | Cancel own appointment (status 0/1/5 → 2). Rejects cancelled/completed/no-show (409); past appointments rejected (400) |
| GET | `/prescriptions` | `?page=1&size=10` | My prescriptions |
| GET | `/bills` | `?page=1&size=10` | My bills |
| GET | `/export` | — | HIPAA Right of Access — full data export (demographics + appointments + prescriptions + bills) |
| GET | `/observations` | `?loinc=&page=1&size=20` | My lab results — `PageResult<Observation>`; `loinc` filters server-side |
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
| PUT | `/bills/{id}/pay` | body: {paymentAmount, paymentMethod} | Pay own bill (PENDING → PAID). Ownership verified. DRAFT is not payable |
| PUT | `/password` | body: {oldPassword, newPassword} | Change password (enforces complexity + history policy) |
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
| POST | `/` | ADMIN,DOCTOR | body: PrescriptionFormDTO | Create + items (CDS interaction/allergy check first) |
| DELETE | `/{id}` | ADMIN | path | Soft-delete + items (hidden for transmitted/dispensed/cancelled) |
| PUT | `/{id}/cancel` | ADMIN,DOCTOR | path | Cancel prescription (active→cancelled). Rejects non-active (409). Prescriptions are cancel-reissue — no in-place edit endpoint (Round 28/34) |

### Prescription Refill Requests — `/api/v1/prescriptions/refill-requests`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/` | ADMIN,DOCTOR | Pending refill requests (patient → doctor approval workflow) |
| PUT | `/{id}/approve` | ADMIN,DOCTOR | Approve a refill request |
| PUT | `/{id}/deny` | ADMIN,DOCTOR | body: {notes?} — deny a refill request |

### Billing — `/api/v1/bills`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&patientId=` | Paginated list (DOCTOR scoped to own patients) |
| GET | `/{id}` | ADMIN,DOCTOR | path | Bill detail |
| POST | `/` | ADMIN,DOCTOR | body: BillFormDTO | Create bill (DRAFT) |
| PUT | `/{id}/submit` | ADMIN,DOCTOR | path | Submit claim (DRAFT → SUBMITTED) |
| PUT | `/{id}/adjudicate` | ADMIN | body: {insurancePayment, adjustment, claimNumber, adjudicationDate} | Adjudicate (SUBMITTED/PENDING). Rejects PAID/DENIED (409) |
| PUT | `/{id}/pay` | ADMIN,DOCTOR | body: {paymentAmount, paymentMethod} | Staff-side payment (PENDING → PAID). DRAFT not payable. Rejects PAID/DENIED (409) |
| PUT | `/{id}/deny` | ADMIN | body: {reason} | Deny claim (PENDING → DENIED). Rejects PAID (409) |
| DELETE | `/{id}` | ADMIN | path | Soft-delete |

Claim lifecycle: DRAFT → SUBMITTED → (adjudicate) → PENDING → (pay) PAID / (deny) DENIED.

### Charges (Superbill) — `/api/v1/charges`

Requires `ADMIN` or `DOCTOR`. Charge capture linked to appointments, convertible to bills.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10` | Paginated charge list |
| POST | `/` | body: {patientId, appointmentId?, cptCodes?, icd10Codes?, chargeAmount?, visitType?, notes?} | Create charge (DRAFT) |
| PUT | `/{id}/convert` | path | Convert DRAFT charge → bill (status → BILLED, creates Bill) |

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
| GET | `/check` | `?rxnormCode=&insurancePayer=` | Coverage check → `{found, drugName?, tier?, priorAuthRequired?, stepTherapyRequired?, alternatives?}` or `{found: false, message}` |
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
| GET | `/stats` | Aggregate stats (cached 30 min): totals, revenue, trends |

### Export — `/api/v1/export`

Requires `ADMIN` or `DOCTOR`. DOCTOR role is scoped to own patients only (from appointments/prescriptions); ADMIN exports all records.

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

### FHIR — `/api/v1/fhir`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/metadata` | public | CapabilityStatement (FHIR 4.0.1 + SMART on FHIR security) |
| GET | `/Patient/{id}` | ADMIN,DOCTOR | FHIR Patient resource (SSN masked to last-4) |
| GET | `/Patient` | ADMIN,DOCTOR | FHIR search (`?_id=100`) returning Bundle |

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
| POST | `/access/{patientId}` | body: {reason} | Returns short-lived (30min) JWT with `scope=EMERGENCY` + `patientId` claim. Use this token to access the specific patient's data via `/patients/{id}` |
| GET | `/history` | `?patientId=&audited=0` | View emergency access history — filter by patient or unreviewed (ADMIN only) |
| PUT | `/{id}/review` | path | Mark emergency access as reviewed — sets `audited=1`, `reviewedBy`, `reviewedAt` (ADMIN only) |

### Key Management — `/api/v1/admin/keys`

Requires `ADMIN`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/history` | Key lifecycle audit trail (KEY_INIT / KEY_ROTATION events) |
| POST | `/rotate` | Trigger runtime key rotation — body: {newKey, oldKey} |
| GET | `/rotation-status` | Key rotation migration progress — per-table legacy row counts |

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
| POST | `/adt` | body: AdtEventDTO | ADT event (A01/A03/A08) — upsert Patient by MRN |
| POST | `/lab-results` | body: LabResultDTO | Batch lab results with sourceMessageId dedup |

### Lab Results — `/api/v1/patients/{id}/observations` + `/api/v1/loinc`

Requires `ADMIN` or `DOCTOR` (catalog also `PATIENT`). Lab trend analysis and LOINC catalog.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/patients/{id}/observations` | `?loinc=&page=1&size=20` | Paginated lab results (`PageResult<Observation>`); `loinc` filters server-side |
| GET | `/patients/{id}/observations/trend` | `?loinc=` (required) | Full history of one test for trend rendering |
| GET | `/loinc/catalog` | — | Full LOINC dictionary (ADMIN,DOCTOR,PATIENT) |
| GET | `/loinc/panel/{parentCode}` | path | LOINC codes grouped by panel (CBC/BMP/LIPID) |

### FHIR Observation — `/api/v1/fhir/Observation`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/Observation/{id}` | ADMIN,DOCTOR | Single FHIR Observation resource |
| GET | `/Observation?patient=` | ADMIN,DOCTOR | Observations by patient (Bundle) |

### Pharmacy — `/api/v1/pharmacies`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?zip=&state=` | Search pharmacy directory |

### ePrescribing — `/api/v1/prescriptions/{id}/transmit`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| PUT | `/{id}/transmit` | `?pharmacyId=` | Transmit prescription via NCPDP SCRIPT, EPCS audit if controlled substance |

### eCQM — `/api/v1/quality`

Requires `ADMIN` or `DOCTOR`. CMS MIPS/MACRA clinical quality measures.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/measures` | — | List all quality measure definitions |
| GET | `/measures/{cmsId}/report` | path | Latest persisted performance report (CMS122/CMS125/CMS165) |
| POST | `/measures/{cmsId}/calculate` | path | Run the measure calculation now; result persisted to `quality_result` |
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
| DOCTOR | Patient/appointment/prescription/bill CRUD, dashboard, export, chat, own profile |
| PATIENT | Patient portal (`/api/v1/patient/me/*`), patient chat (`/api/v1/patient/me/messages/*`) |

### Data Encryption
- **Passwords**: BCrypt hashed + complexity policy (8+ chars, upper/lower/digit/special) + history enforcement (last 3 cannot be reused)
- **PHI fields**: AES-256-GCM via `@Convert(converter = AesAttributeConverter.class)`, versioned key format supporting rotation (`app.aes.key` / `app.aes.key.previous`)
- **Redis cache safety**: `PhiMaskingRedisSerializer` automatically redacts `@PhiField`-annotated DTO fields to `[PHI-REDACTED]`
- **Token validation**: External IdP JWKS; no local secret management needed

### Account Security
- System users: 5 failed logins → 15-minute lockout (matching patient lockout)
- Patients: 5 failed logins → 15-minute lockout
- Token expiry: configurable via `app.security.access-token-expiry-seconds` (default 7200s)
- Security headers: HSTS (1yr), X-Content-Type-Options, X-Frame-Options DENY, XSS Protection, Cache-Control

### Data Retention
- Audit logs: nightly purge of records older than `app.retention.audit-log-days` (default 2190 = 6 years)
- Soft-deleted records: retained for `app.retention.soft-delete-days` (default 365 days) before permanent removal

## Infrastructure

### Cache (Redis)

| Cache | Key | TTL | Eviction |
|-------|-----|-----|----------|
| `patients` | `#id` | 30 min | on create → all; on update/delete → by id |
| `users` | `#id` | 30 min | same pattern |
| `dashboard` | `'stats'` | 30 min | none |

### Audit Logging
AOP-based via `@Auditable(module, action)`. Captures userId, username, module, action, targetId, IP, timestamp → `audit_log` table. Applied to all CUD service operations. **21 CFR Part 11 compliant:** SHA-256 `row_hash` for tamper detection, soft-delete (`archived` flag) instead of physical deletion, login success/failure audited with reason codes.

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
