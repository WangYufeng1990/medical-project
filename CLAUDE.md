# CLAUDE.md — Medical Management System

## Project Overview

HIPAA-compliant medical practice management system. Spring Boot backend + React frontend. FHIR R4 interoperability, 21 CFR Part 11 audit compliance, AES-256-GCM encryption, clinical decision support.

---

## Tech Stack (Mandatory)

| Layer | Technology | Version / Note |
|-------|-----------|---------------|
| Language | Java | 17 (LTS) |
| Framework | Spring Boot | 3.x (latest stable) |
| Build Tool | Maven | wrapper included |
| ORM | Spring Data JPA + Querydsl | Hibernate 6.x provider |
| Database | MySQL | 8.0+ |
| Cache | Redis | 7.x, accessed via Spring Cache + Redisson |
| Auth | Spring Boot OAuth2 Resource Server | external IdP (Okta / Auth0 / AWS Cognito) |
| FHIR | HAPI FHIR R4 | 7.x (org.hl7.fhir.r4) |
| API Doc | Springdoc OpenAPI | 2.7.0 (Swagger UI at /doc.html) |
| Validation | Jakarta Validation + Hibernate Validator | bundled with Spring Boot |
| JSON | Jackson | bundled with Spring Boot |
| Util | Lombok, Hutool | latest |
| Testing | JUnit 5 + Spring Boot Test | 121 tests (112 integration + 9 unit) |
| Frontend | React 18 + TypeScript + Vite 5 | medical-web/ |

**Explicitly excluded (DO NOT introduce):**
- No MyBatis/MyBatis-Plus (Spring Data JPA is the single ORM)
- No Spring Cloud / microservices (this is a monolithic backend)
- No gRPC, GraphQL (REST only)
- No Elasticsearch, MongoDB, Neo4j (MySQL + Redis only)
- No message queues (RabbitMQ, Kafka, RocketMQ) unless a concrete async requirement arises
- No MapStruct, ModelMapper or any object-mapping library. DTO ↔ Entity conversion logic MUST be encapsulated inside DTO classes (static factory `fromEntity()` / instance `toEntity()` methods). Never scatter conversion code in Controllers or Services.
- No Shiro (Spring Security is the single auth framework)
- No self-issued JWT (OAuth2 Resource Server handles token validation against external IdP JWKS endpoint)

---

## Project Structure

```
medical-project/
├── CLAUDE.md                           # this file
├── README.md                           # project overview + quick start
├── docs/                               # design docs, interview prep, roadmap
│   ├── API-LAYOUT.md
│   ├── backend-architecture-explained.md
│   ├── medical-learning-guide.md
│   ├── LEARNING-ORDER.md
│   ├── ROADMAP.md
│   ├── INTERVIEW-BACKEND.md
│   └── INTERVIEW-FRONTEND.md
├── medical-server/                     # Spring Boot backend
│   └── src/main/java/com/example/medical/
│       ├── MedicalApplication.java
│       ├── common/
│       │   ├── annotation/             # @PhiField (Redis cache PHI redaction)
│       │   ├── audit/                  # @Auditable, AuditLogAspect, AuditLogWriter,
│       │   │                           # AuditLog, AuditLogVO, AuditLogService,
│       │   │                           # AuditLogController, KeyAudit, KeyAuditController
│       │   ├── base/                   # BaseEntity, PageQuery
│       │   ├── config/                 # SecurityConfig, AesCryptoUtil, FhirConfig,
│       │   │                           # CacheConfig, RateLimiterConfig, DataInitializer, etc.
│       │   ├── enums/                  # ResultCode
│       │   ├── exception/             # GlobalExceptionHandler, BusinessException
│       │   ├── job/                    # DataRetentionJob (scheduled audit archive)
│       │   ├── result/                 # Result<T>, PageResult<T>
│       │   └── validation/            # @ValidPassword, PasswordPolicyValidator
│       ├── module/
│       │   ├── system/                 # users, roles, menus, auth, emergency access
│       │   ├── patient/                # patients, FHIR, consent, observations, LOINC
│       │   ├── appointment/            # scheduling with conflict detection
│       │   ├── prescription/           # prescriptions + CDS + ePrescribing + EPCS
│       │   ├── billing/                # insurance claim lifecycle
│       │   ├── chat/                   # patient-doctor messaging
│       │   ├── dashboard/              # aggregate statistics
│       │   ├── export/                 # streaming CSV export
│       │   ├── integration/            # Mirth Connect ADT + lab results JSON API
│       │   └── quality/                # CMS eCQM quality measures
│       ├── security/                   # JwtClaimMapper, LoginUser, DevJwtEncoder
│       └── util/                       # CsvUtil
│       └── resources/
│           ├── application.yml
│           ├── application-dev.yml
│           ├── application-h2.yml
│           ├── application-prod.yml
│           └── logback-spring.xml
└── medical-web/                        # React + TypeScript frontend
    └── src/
        ├── api/                        # axios API layer (request interceptor + module APIs)
        ├── layout/                     # StaffLayout, PatientLayout
        └── views/                      # dashboard, login, patients, appointments,
            │                           # prescriptions, billing, profile,
            │                           # system (users/roles/menus),
            │                           # patient portal (dashboard/profile/...)
            └── shared.module.css
```

**Rules:**
- Every business module lives under `module/<module-name>/` with an identical internal layout.
- `common/` is strictly for cross-cutting concerns. Business logic goes into modules.
- No cyclic references between modules. If two modules need the same thing, it belongs in `common/`.
- DTOs go in the module's own `dto/` directory, not a global one.
- JPA repositories live in each module's `repository/` directory. Naming: `{Entity}Repository`.

---

## Development Principles (Non-Negotiable)

### 1. Zero Fluff
- **No docstrings or comments on code that is self-explanatory.**
- A comment is only justified when the WHY is non-obvious.
- No README/implementation-plan documents unless explicitly asked.

### 2. Dependency Discipline
- **Do not add a dependency unless it solves a real, concrete problem.**
- Always prefer the standard library, Spring Boot built-ins, or the stack already listed.
- No "just in case" or "we might need it later" dependencies.

### 3. Code Generation Discipline
- **Do not generate boilerplate code proactively.**
- When asked to implement something, generate only the files directly needed.
- Three similar lines of code is better than a premature abstraction.

### 4. Error Handling
- Use a global `@RestControllerAdvice` handler. Controllers should throw, not catch.
- Return `Result<T>` for all API responses — never raw entities, never bare strings.
- Business exceptions extend a single `BusinessException(baseCode, message)`.

### 5. API Design
- All endpoints return `Result<T>`: `{ "code": 200, "message": "ok", "data": {...} }`
- HTTP verbs strictly by semantics: GET reads, POST creates, PUT full updates, PATCH partial, DELETE deletes.
- Path naming: `/api/v1/<module>/<resource>`, e.g., `/api/v1/patients/{id}`
- All paginated endpoints MUST return `Result<PageResult<T>>`.

### 6. Security
- OAuth2 Resource Server validates JWTs against external IdP JWKS endpoint.
- Passwords: BCrypt hashed. Only local fallback accounts use this.
- PHI fields: MUST use `@Convert(converter = AesAttributeConverter.class)` on entity fields. NEVER write manual encrypt/decrypt wrappers in Service classes.
- RBAC via `@PreAuthorize("hasRole('ADMIN')")`, mapping JWT claims via custom `JwtAuthenticationConverter`.
- Dev-mode authentication requires explicit `app.security.dev-mode: true` — must not auto-detect.
- AES-256-GCM with versioned ciphertext. Key derivation: PBKDF2-HMAC-SHA256 310k iterations.
- Audit logs have SHA-256 row_hash. Use archived flag, never DELETE.

### 7. Database
- All tables MUST have `id`, `create_time`, `update_time` — inherited from `BaseEntity`.
- Logical delete only (`is_deleted` flag) via `@SQLDelete` / `@SQLRestriction`.
- Medical data: use `@Version` optimistic locking on critical entities.
- Audit log: immutable (archived flag + row_hash), never physically deleted.

### 8. Testing
- Write tests only when asked.
- Happy path + one edge case + one failure mode. No more.
- Use `@WebMvcTest` for controllers, `@DataJpaTest` for repositories.

### 9. Git
- Do not init or commit unless explicitly asked.
- Small, focused commits. Messages in English present tense.

### 10. Response Style
- Be concise. State what you are about to do, do it, report the result.
- No summaries, no emojis, no markdown tables unless data comparison requires it.
- If you hit a blocker or ambiguity, ask — don't guess.
