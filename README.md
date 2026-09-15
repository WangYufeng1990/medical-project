# Medical Management System

HIPAA-compliant medical practice management system. Spring Boot 3.4 + React 18 + MySQL + Redis + Okta OAuth2 (implemented in code; currently runs on local dev JWT — see Architecture doc §5.1).

## Quick Start

```bash
# 1. Start backend — an explicit profile is REQUIRED (no default; Review III C5)
#    h2 = local file DB, no external services (no MySQL, no Redis, no Okta)
#    DB file: ~/.medical-dev/data/medical_dev (CWD-independent; override with H2_DB_PATH)
cd medical-server && SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run

# 2. Start frontend
cd medical-web && npm run dev

# Frontend: http://localhost:5173
# Backend:  http://localhost:8080
# API docs: http://localhost:8080/doc.html
```

Prerequisites: JDK 17+ and Maven (3.8+) for the backend, **Node 20.9+** for the
frontend (`medical-web/.nvmrc` pins it — `nvm use` in that directory). Node 16
and below fail with `structuredClone is not defined` from inside ESLint rather
than anything resembling a version error, so check `node -v` before blaming the
config. The `h2` profile needs nothing else — Redis is only required by the
`dev` and `prod` profiles.

### Local database (h2 profile)

The schema lives in `medical-server/src/main/resources/sql/schema.sql` and is
applied on every boot with `spring.sql.init.mode=always`. Every statement is
`CREATE TABLE IF NOT EXISTS`, so it **never alters a database that already
exists**.

> **After changing `schema.sql` you must rebuild the local database** — delete
> `~/.medical-dev/data/` (or point `H2_DB_PATH` at a new file) and restart:
>
> ```bash
> rm -rf ~/.medical-dev/data && cd medical-server && SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run
> ```

Forgetting this is now a loud failure rather than a mysterious one:
`DevSchemaGuard` records the schema version the file was built with and refuses
to start on a mismatch, telling you which version the file is and how to reset.
When you edit `schema.sql`, bump `SCHEMA_VERSION` in
`common/config/DevSchemaGuard.java` in the same commit.

`H2_DB_PATH` overrides the database location — a plain path, not a JDBC URL
(the URL template already supplies `jdbc:h2:file:`):

```bash
H2_DB_PATH=./data/medical_dev SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run   # relative to CWD
H2_DB_PATH=/absolute/path/to/db SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run
# default: ${user.home}/.medical-dev/data/medical_dev
```

### Rate limiting

The `h2` profile disables rate limiting so it stays dependency-free (Redisson's
auto-configuration is excluded for that profile — it connects eagerly and would
otherwise abort startup without a Redis). To exercise the limiters locally:

```bash
# 1. remove `spring.autoconfigure.exclude` from application-h2.yml
# 2. set app.rate-limit.enabled: true in application-h2.yml
# 3. run a Redis on localhost:6379 (REDIS_HOST / REDIS_PORT override)
cd medical-server && SPRING_PROFILES_ACTIVE=h2 mvn spring-boot:run
```

Limits (`common/config/RateLimiterConfig.java`): 10 logins/min/IP, 20 token
refreshes/min/IP, 5 exports/hour/IP (`app.rate-limit.export-per-hour`, raised to
100 for h2), 5 password resets/min/IP. The effective limit is part of the Redis
key (`rate:export:100:3600:<ip>`), so a changed limit applies immediately;
counters written for a previous limit are left behind inert. Clear counters only
when you want to un-throttle yourself while testing:

```bash
# Redisson keeps three keys per limiter — the config hash plus brace-wrapped
# `{...}:permits` and `{...}:value` companions. Deleting only the first leaves
# the consumed budget in place, so match all of them:
redis-cli --scan --pattern '*rate:*' | xargs -r redis-cli del
```

Profiles: `h2` / `dev` (local, seeded demo data; `dev` additionally needs MySQL
and Redis) · `prod` (requires `AES_KEY`, `JWT_SIGNING_KEY` ≥32 chars and
independent of `AES_KEY`, `DB_USER`, `DB_PASSWORD`; startup fails fast via
`ProdGuard` if any are missing).

**Default accounts (dev/h2 profiles only — seed data never runs in prod):**

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `admin123` |
| Doctor | `doctor1` | `doctor123` |
| Patient | `patient1` | `patient123` |
| Patient 2 | `patient2` | `patient123` |
| Patient 3 | `patient3` | `patient123` |
| Patient 4 | `patient4` | `patient123` |

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4 |
| Frontend | React 18 + TypeScript + Vite 5 |
| ORM | Spring Data JPA |
| Database | MySQL 8.0 / H2 (dev) |
| Cache | Redis 7 (Redisson + Spring Cache) |
| Auth | Spring Boot OAuth2 Resource Server (Okta / dev JWT) |
| FHIR | HAPI FHIR R4 7.4 |
| API Doc | Springdoc OpenAPI (Swagger UI at /doc.html) |

## HIPAA Compliance

| Standard | Implementation |
|----------|---------------|
| §164.312(a) Access Control | Okta OAuth2 + RBAC (ADMIN/DOCTOR/PATIENT) + account lockout (5 failures/15min) + password complexity policy + HSTS/security headers |
| §164.312(b) Audit Controls | AOP `@Auditable` + async `REQUIRES_NEW` audit log + PHI/credential redaction + ADMIN query API (`/api/v1/audit-logs`) + tamper-evident SHA-256 hash chain + `GET /api/v1/audit-logs/verify` |
| §164.312(d) Person Authentication | BCrypt + `@ValidPassword` (8+ chars, upper/lower/digit/special) + password history (last 3) |
| §164.312(e) Transmission Security | TLS (MySQL SSL) + HSTS (1yr) + CORS whitelist |
| §164.508 Consent | `consent` table + CRUD API + patient self-service view |
| §164.524 Right of Access | Patient self-service export (`GET /api/v1/patient/me/export`) |
| Data-at-Rest Encryption | AES-256-GCM via JPA `@Convert` — transparent encrypt/decrypt with versioned key rotation |
| Emergency Access | Break-glass endpoint (`/api/v1/emergency`) — 30min JWT expiry + audit with ADMIN review flow |
| Data Retention | Scheduled nightly purge — audit logs 6yr (2190 days) |
| Clinical Decision Support | Drug-Drug Interaction + Drug-Allergy contraindication check (`/api/v1/cds/check`) |
| Integration Engine | Mirth Connect JSON API — ADT events + lab results with dedup |
| LOINC Lab Coding | 29-code catalog + auto-flag (LL/L/N/H/HH) + trend analysis |
| ePrescribing + EPCS | NCPDP SCRIPT draft XML generation — controlled substances fail-closed (never marked "transmitted" until a real 21 CFR Part 1311 channel exists) |
| eCQM Quality Measures | CMS122/125/165 performance reports evaluated over decrypted data (in-memory) |
| 21 CFR Part 11 Audit | Immutable audit log (SHA-256 hash chain + soft-delete) + login failure audit + role/menu change audit |
| Encryption | AES-256-GCM + PBKDF2-HMAC-SHA256 (310k iterations) + versioned key rotation |
| Anti-DoS | Pagination on FHIR/emergency/pharmacy endpoints + streaming CSV export + refresh rate limiting |
| Frontend RBAC | JWT role-based sidebar filtering + AdminGuard route protection + patient self-edit with HIPAA field restrictions |

## FHIR R4 Interoperability

| Endpoint | Description |
|----------|-------------|
| `GET /api/v1/fhir/metadata` | CapabilityStatement (4.0.1 + SMART on FHIR) |
| `GET /api/v1/fhir/Patient/{id}` | FHIR Patient resource (SSN masked) |
| `GET /api/v1/fhir/Patient?_id=` | FHIR search (Bundle) |
| `GET /api/v1/patients/{id}/case` | Comprehensive patient Bundle |

US Core compliant: OMB race/ethnicity Coding extensions. SMART on FHIR OAuth2 scopes in JWT.

## API Overview

| Module | Base Path | Auth |
|--------|-----------|------|
| Auth | `/api/v1/auth` | public |
| Patient Auth | `/api/v1/patient/login` | public |
| Users | `/api/v1/users` | ADMIN |
| User Profile | `/api/v1/users/me` | authenticated |
| Roles | `/api/v1/roles` | ADMIN |
| Menus | `/api/v1/menus` | ADMIN |
| Patients | `/api/v1/patients` | ADMIN,DOCTOR |
| Patient Portal | `/api/v1/patient/me` | PATIENT |
| Appointments | `/api/v1/appointments` | ADMIN,DOCTOR |
| Prescriptions | `/api/v1/prescriptions` | ADMIN,DOCTOR |
| Bills | `/api/v1/bills` | ADMIN,DOCTOR |
| Messages | `/api/v1/messages` | ADMIN,DOCTOR,PATIENT |
| Dashboard | `/api/v1/dashboard` | ADMIN,DOCTOR |
| Export | `/api/v1/export` | ADMIN,DOCTOR |
| Audit Logs | `/api/v1/audit-logs` (+ `/verify` tamper check) | ADMIN |
| FHIR | `/api/v1/fhir` | mixed |
| Consent | `/api/v1/consent` | ADMIN,DOCTOR |
| Emergency | `/api/v1/emergency` | ADMIN,DOCTOR |
| Key Audit | `/api/v1/admin/keys` | ADMIN |
| CDS | `/api/v1/cds` | ADMIN,DOCTOR |
| Integration | `/api/v1/integration` | ADMIN,DOCTOR |
| Lab Results | `/api/v1/patients/{id}/observations` | ADMIN,DOCTOR |
| LOINC | `/api/v1/loinc` | ADMIN,DOCTOR,PATIENT |
| Pharmacy | `/api/v1/pharmacies` | ADMIN,DOCTOR |
| eCQM | `/api/v1/quality` | ADMIN,DOCTOR |
| Patient Export | `/api/v1/patient/me/export` | PATIENT |
| Account Unlock | `/api/v1/users/{id}/unlock` | ADMIN |
| eCQM Calculate | `/api/v1/quality/measures/{cmsId}/calculate` | ADMIN |
| Referrals | `/api/v1/referrals` | ADMIN,DOCTOR |
| Refill Requests | `/api/v1/prescriptions/refill-requests` | ADMIN,DOCTOR |
| Problems | `/api/v1/patients/{id}/problems` | ADMIN,DOCTOR |
| Immunizations | `/api/v1/patients/{id}/immunizations` | ADMIN,DOCTOR |
| Care Plans | `/api/v1/patients/{id}/care-plans` | ADMIN,DOCTOR |
| Prior Auths | `/api/v1/prior-auths` | ADMIN,DOCTOR |
| Charges (Superbill) | `/api/v1/charges` | ADMIN,DOCTOR |
| Formulary | `/api/v1/formulary` | ADMIN,DOCTOR |
| Chat SSE Ticket | `/api/v1/chat/sse-ticket` | ADMIN,DOCTOR,PATIENT |
| Password Reset | `/api/v1/patient/forgot-password` | public |

## Project Structure

```
medical-server/src/main/java/com/example/medical/
├── common/
│   ├── annotation/      @PhiField
│   ├── audit/           @Auditable, AuditLogAspect, AuditLogWriter,
│   │                    AuditLogController, KeyAudit
│   ├── base/            BaseEntity, PageQuery
│   ├── config/          SecurityConfig, AesCryptoUtil, FhirConfig,
│   │                    CacheConfig, RateLimiterConfig, DataInitializer
│   ├── job/             DataRetentionJob, AppointmentScheduler,
│   │                     QualityScheduler
│   ├── validation/      @ValidPassword, PasswordPolicyValidator
│   ├── exception/       GlobalExceptionHandler, BusinessException
│   ├── result/          Result<T>, PageResult<T>
│   └── enums/           ResultCode
├── security/            JwtClaimMapper, LoginUser
├── module/
│   ├── system/          users, roles, menus, auth, emergency access
│   ├── patient/         patients, patient portal, FHIR, consent, FHIR Observation
│   ├── appointment/     scheduling
│   ├── prescription/    prescriptions + items, CDS, ePrescribing, EPCS, pharmacy
│   ├── billing/         bills + payments lifecycle
│   ├── chat/            patient-doctor messaging
│   ├── dashboard/       aggregate stats
│   ├── export/          CSV export
│   ├── integration/     Mirth Connect ADT + lab results JSON API
│   └── quality/         CMS eCQM quality measures
└── util/                CsvUtil
```

## Configuration

Key properties in `application.yml`:

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:}   # REQUIRED: prod | dev | h2 — no default (ProdGuard fails fast)

app:
  aes:
    key: ${AES_KEY}              # AES-256-GCM encryption key (required)
    key.previous:                 # optional — previous key during/after rotation
  security:
    access-token-expiry-seconds: 7200
    dev-mode: false               # dev/h2 profiles explicitly enable local JWT signing
    dev-jwt-secret:               # dev/h2 only — no hardcoded fallback
  retention:
    audit-log-days: 2190          # 6 years HIPAA minimum
  cors:
    allowed-origins: http://localhost:5173
  integration:
    api-key:                      # X-Integration-Key header for Mirth Connect
                                  # (fail-closed: unset ⇒ 403; h2/dev default dev-integration-key)
  rate-limit:
    enabled: true                 # Redisson login/refresh/export rate limiting
                                  # (default; the h2 profile sets this to false and
                                  #  excludes RedissonAutoConfigurationV2 — see Quick Start)

# Profile overrides:
#   dev  → MySQL, rate limiting off (still needs Redis for the Redisson client)
#   h2   → file DB, rate limiting off, Redisson excluded (no Redis needed)
#   prod → all of the below are required, checked by ProdGuard

# Production (prod profile) — all required, checked by ProdGuard:
# SPRING_PROFILES_ACTIVE=prod
# AES_KEY=...
# JWT_SIGNING_KEY=...             # ≥32 chars, INDEPENDENT from AES_KEY (key separation)
# DB_USER=... DB_PASSWORD=...
```

## Checks before committing

```bash
# Backend — 166 tests plus the build-environment guardrails (enforcer).
# Use `clean`: Surefire never deletes old reports, so `target/surefire-reports`
# keeps counting test classes that no longer exist.
cd medical-server && mvn clean verify

# Frontend — ESLint (the recurring-bug rules from CLAUDE.md) + tsc --noEmit
cd medical-web && npm run check

# Production bundle
cd medical-web && npm run build
```

`npm run check` is the pair of `lint` + `typecheck`; both run standalone too. The
backend guardrails (`maven-enforcer-plugin`) check the Maven/Java version,
duplicate POM dependency versions and dependency convergence — the last one is
why `hapi-fhir-base` is pinned in `dependencyManagement`.

## Documentation

- [Mirth Connect Integration](docs/MIRTH-CONNECT-INTEGRATION.md)
- [API Layout & Reference](docs/API-LAYOUT.md)
- [Architecture Explained](docs/backend-architecture-explained.md)
- [Roadmap](docs/ROADMAP.md)
- [Development Rules](CLAUDE.md)
