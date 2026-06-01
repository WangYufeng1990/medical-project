# Medical Management System

HIPAA-compliant medical practice management backend with FHIR R4 interoperability. Spring Boot 3.4 + MySQL + Redis + Okta OAuth2.

## Quick Start

```bash
# 1. Start MySQL & Redis
brew services start mysql redis

# 2. Create database
mysql -u root -e "CREATE DATABASE IF NOT EXISTS medical_dev CHARACTER SET utf8mb4"
mysql -u root -e "CREATE USER IF NOT EXISTS 'medical'@'localhost' IDENTIFIED BY 'medical123'"
mysql -u root -e "GRANT ALL ON medical_dev.* TO 'medical'@'localhost'"

# 3. Run with H2 (no external DB needed)
cd medical-server
./mvnw spring-boot:run -Dspring-boot.run.profiles=h2

# 4. Run with MySQL dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 5. Run tests
./mvnw test
```

**Default accounts (dev/h2):**

| Role | Username | Password |
|------|----------|----------|
| Admin | `admin` | `admin123` |
| Doctor | `doctor1` | `doctor123` |
| Patient | `patient1` | `patient123` |

API docs: http://localhost:8080/doc.html

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 17 |
| Framework | Spring Boot 3.4 |
| ORM | Spring Data JPA + Querydsl |
| Database | MySQL 8.0 / H2 (dev) |
| Cache | Redis 7 (Redisson + Spring Cache) |
| Auth | Spring Boot OAuth2 Resource Server (Okta) |
| FHIR | HAPI FHIR R4 7.4 |
| API Doc | Knife4j (Swagger) |

## HIPAA Compliance

| Standard | Implementation |
|----------|---------------|
| §164.312(a) Access Control | Okta OAuth2 + RBAC (ADMIN/DOCTOR/PATIENT) + account lockout (5 failures/15min) + password complexity policy + HSTS/security headers |
| §164.312(b) Audit Controls | AOP `@Auditable` + async `REQUIRES_NEW` audit log + PHI masking + ADMIN query API (`/api/v1/audit-logs`) |
| §164.312(d) Person Authentication | BCrypt + `@ValidPassword` (8+ chars, upper/lower/digit/special) + password history (last 3) |
| §164.312(e) Transmission Security | TLS (MySQL SSL) + HSTS (1yr) + CORS whitelist |
| §164.508 Consent | `consent` table + CRUD API + patient self-service view |
| §164.524 Right of Access | Patient self-service export (`GET /api/v1/patient/me/export`) |
| Data-at-Rest Encryption | AES-256-GCM via JPA `@Convert` — transparent encrypt/decrypt with versioned key rotation |
| Emergency Access | Break-glass endpoint (`/api/v1/emergency`) — 30min expiry + synchronous audit |
| Data Retention | Scheduled nightly purge — audit logs 6yr (2190 days) |

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
| Audit Logs | `/api/v1/audit-logs` | ADMIN |
| FHIR | `/api/v1/fhir` | mixed |
| Consent | `/api/v1/consent` | ADMIN |
| Emergency | `/api/v1/emergency` | ADMIN,DOCTOR |
| Key Audit | `/api/v1/admin/keys` | ADMIN |

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
│   ├── job/             DataRetentionJob
│   ├── validation/      @ValidPassword, PasswordPolicyValidator
│   ├── exception/       GlobalExceptionHandler, BusinessException
│   ├── result/          Result<T>, PageResult<T>
│   └── enums/           ResultCode
├── security/            JwtClaimMapper, LoginUser
├── module/
│   ├── system/          users, roles, menus, auth, emergency access
│   ├── patient/         patients, patient portal, FHIR, consent
│   ├── appointment/     scheduling
│   ├── prescription/    prescriptions + items
│   ├── billing/         bills + payments lifecycle
│   ├── chat/            patient-doctor messaging
│   ├── dashboard/       aggregate stats
│   └── export/          CSV export
└── util/                CsvUtil
```

## Configuration

Key properties in `application.yml`:

```yaml
app:
  aes:
    key: ${AES_KEY}              # AES-256-GCM encryption key
    key.previous:                 # optional — previous key for rotation
  security:
    access-token-expiry-seconds: 7200
  retention:
    audit-log-days: 2190          # 6 years HIPAA minimum
  cors:
    allowed-origins: http://localhost:5173
```

## Documentation

- [API Layout & Reference](API-LAYOUT.md)
- [Architecture Explained](backend-architecture-explained.md)
- [Medical Domain Learning Guide](medical-learning-guide.md)
- [Development Rules](CLAUDE.md)
