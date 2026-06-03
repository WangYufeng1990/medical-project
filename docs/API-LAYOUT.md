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
| API Doc | Knife4j (Swagger) |
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
│   ├── prescription/    prescriptions + items (CRUD)
│   ├── billing/         bills + payment
│   ├── chat/            patient-doctor messaging
│   ├── dashboard/       aggregate stats (JdbcTemplate)
│   └── export/          CSV export (patients, bills)
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

---

## API Endpoints

### Auth — `/api/v1/auth`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/login` | public | Staff/patient login, redirects to IdP or validates local credentials |
| POST | `/refresh` | Bearer token | Refresh OAuth2 access token via IdP |
| POST | `/logout` | Bearer token | Invalidate session at IdP / notify client to discard token |

### Patient Auth — `/api/v1/patient`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/login` | public | Patient login |

### User Management — `/api/v1/users`

All require `ADMIN` role.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=10&keyword=` | Paginated user list |
| GET | `/{id}` | path | User detail (cached) |
| POST | `/` | body: SysUserFormDTO | Create user |
| PUT | `/{id}` | path + body | Update user (evicts cache) |
| DELETE | `/{id}` | path | Soft-delete user (evicts cache) |

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
| DELETE | `/{id}` | ADMIN | path | Soft-delete patient |

### Patient Portal — `/api/v1/patient/me`

All require `PATIENT` role.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | — | Current patient profile |
| GET | `/appointments` | `?page=1&size=10` | My appointments |
| GET | `/prescriptions` | `?page=1&size=10` | My prescriptions |
| GET | `/bills` | `?page=1&size=10` | My bills |
| GET | `/export` | — | HIPAA Right of Access — full data export (demographics + appointments + prescriptions + bills) |
| GET | `/consent` | — | My consent records |
| PUT | `/password` | body: {oldPassword, newPassword} | Change password (enforces complexity + history policy) |

### Appointments — `/api/v1/appointments`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&status=` | Paginated list |
| GET | `/{id}` | ADMIN,DOCTOR | path | Appointment detail |
| POST | `/` | ADMIN,DOCTOR | body: AppointmentFormDTO | Create (30-min conflict check) |
| PUT | `/{id}` | ADMIN,DOCTOR | path + body | Update (30-min conflict check) |
| DELETE | `/{id}` | ADMIN | path | Soft-delete |

Appointment statuses: 0 = Scheduled, 1 = Completed, 2 = Cancelled.

### Prescriptions — `/api/v1/prescriptions`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10` | Paginated list |
| GET | `/{id}` | ADMIN,DOCTOR | path | Prescription detail with items |
| POST | `/` | ADMIN,DOCTOR | body: PrescriptionFormDTO | Create + items |
| PUT | `/{id}` | ADMIN,DOCTOR | path + body: PrescriptionUpdateFormDTO | Update header + replace items |
| DELETE | `/{id}` | ADMIN | path | Soft-delete + items |

### Billing — `/api/v1/bills`

| Method | Path | Auth | Params | Description |
|--------|------|------|--------|-------------|
| GET | `/` | ADMIN,DOCTOR | `?page=1&size=10&status=` | Paginated list |
| GET | `/{id}` | ADMIN,DOCTOR | path | Bill detail |
| POST | `/` | ADMIN,DOCTOR | body: BillFormDTO | Create bill |
| PUT | `/{id}/pay` | ADMIN | path | Mark as paid |
| DELETE | `/{id}` | ADMIN | path | Soft-delete |

Bill statuses: 0 = Unpaid, 1 = Paid, 2 = Refunded.

### Chat (Staff) — `/api/v1/messages`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/conversations` | `?page=1&size=20` | Paginated conversation list |
| GET | `/{partnerId}` | `?page=1&size=50` | Paginated messages with partner (auto-read) |
| POST | `/` | body: MessageFormDTO | Send a message |

### Chat (Patient) — `/api/v1/patient/me/messages`

Requires `PATIENT`. Same endpoints as staff chat.

### Dashboard — `/api/v1/dashboard`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/stats` | Aggregate stats (cached 30 min): totals, revenue, trends |

### Export — `/api/v1/export`

Requires `ADMIN` or `DOCTOR`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/patients` | CSV download of all patients (PHI masked: phone→last4, email→j***@domain) |
| GET | `/bills` | CSV download of all bills (claim numbers masked) |

### Audit Logs — `/api/v1/audit-logs`

Requires `ADMIN`. HIPAA §164.312(b) compliance.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?page=1&size=20&userId=&patientId=&module=&action=&fromDate=&toDate=` | Search/filter audit logs |

### FHIR — `/api/v1/fhir`

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/metadata` | public | CapabilityStatement (FHIR 4.0.1 + SMART on FHIR security) |
| GET | `/Patient/{id}` | ADMIN,DOCTOR | FHIR Patient resource (SSN masked to last-4) |
| GET | `/Patient` | ADMIN,DOCTOR | FHIR search (`?_id=100`) returning Bundle |

### Consent — `/api/v1/consent`

Requires `ADMIN`.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/` | `?patientId=` | List consent records for a patient |
| POST | `/` | body: {patientId, consentType, scope} | Create consent record |
| PUT | `/{id}/revoke` | path | Revoke a consent |

### Emergency Access — `/api/v1/emergency`

Requires `ADMIN` or `DOCTOR`. Break-glass access with mandatory audit.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| POST | `/access/{patientId}` | body: {reason} | Emergency patient data access (30min expiry, synchronously audited) |
| GET | `/history` | `?patientId=` | View emergency access history (ADMIN only) |

### Key Management — `/api/v1/admin/keys`

Requires `ADMIN`.

| Method | Path | Description |
|--------|------|-------------|
| GET | `/history` | Key lifecycle audit trail (KEY_INIT / KEY_ROTATION events) |

### CDS — `/api/v1/cds`

Requires `ADMIN` or `DOCTOR`. Clinical Decision Support — pre-prescription screening.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| POST | `/check` | body: {patientId, items[{rxnormCode, drugName}]} | Check drug-drug interactions + drug-allergy contraindications before prescribing |

### Integration — `/api/v1/integration`

Requires `ADMIN` or `DOCTOR`. Mirth Connect JSON integration for ADT and lab results.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| POST | `/adt` | body: AdtEventDTO | ADT event (A01/A03/A08) — upsert Patient by MRN |
| POST | `/lab-results` | body: LabResultDTO | Batch lab results with sourceMessageId dedup |

### Lab Results — `/api/v1/patients/{id}/observations` + `/api/v1/loinc`

Requires `ADMIN` or `DOCTOR`. Lab trend analysis and LOINC catalog.

| Method | Path | Params | Description |
|--------|------|--------|-------------|
| GET | `/patients/{id}/observations` | `?loinc=` | Lab trend by patient + LOINC code |
| GET | `/loinc/catalog` | — | Full LOINC dictionary |
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
| GET | `/measures/{cmsId}/report` | path | Calculate performance report (CMS122/CMS125/CMS165) |

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
- No physical deletes — `@SQLDelete(sql = "UPDATE {table} SET is_deleted = 1 WHERE id = ?")`
- `@Where(clause = "is_deleted = 0")` on entity level for automatic soft-delete filtering

### FHIR Interoperability
- HAPI FHIR R4 provides standard US healthcare data models.
- `GET /api/v1/patients/{id}/case` returns a FHIR Bundle containing Patient, Condition, AllergyIntolerance, Encounter, and MedicationRequest resources.
- All FHIR resources use standard coding systems (SNOMED CT, LOINC, RxNorm) where applicable.
