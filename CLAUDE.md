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
| API Doc | Springdoc OpenAPI | 2.6.0 (Swagger UI at /doc.html) |
| Validation | Jakarta Validation + Hibernate Validator | bundled with Spring Boot |
| JSON | Jackson | bundled with Spring Boot |
| Util | Lombok, Hutool | latest |
| Testing | JUnit 5 + Spring Boot Test | 153 tests (128 integration + 25 unit) |
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
├── docs/                               # design docs, API reference, roadmap
│   ├── API-LAYOUT.md
│   ├── backend-architecture-explained.md
│   ├── MIRTH-CONNECT-INTEGRATION.md
│   └── ROADMAP.md
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
- **Exception**: Reference/lookup tables populated by seed data (LoincCatalog, DrugInteraction, DrugAllergyClass, PharmacyDirectory, QualityMeasure, QualityResult) do not extend BaseEntity. These are managed via DataInitializer, not user CRUD, so soft-delete and optimistic locking are unnecessary.

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

### 11. Documentation Discipline
- **After every completed feature/round, update `docs/ROADMAP.md`** with the round summary, files changed, and verification results.
- **After every API surface change, update `docs/API-LAYOUT.md`** — new endpoints, modified auth roles, new request/response fields.
- Doc updates are part of the feature — commit them together with the code, not as a separate "docs" commit unless explicitly separated for review.
- If a round includes infrastructure work (agent configs, build changes, patterns), document it in `docs/ROADMAP.md` so the project timeline stays complete.
- Doc-only commits are fine for post-release fixes, verification notes, or lessons-learned additions to existing rounds.

---

## Agent Roles (Plan → Implement → Review)

Every feature/round follows a three-phase workflow. These sections define the scope, patterns, and constraints for each role. **Plan and Review must NOT be done by subagents — use your own context for these phases.** Implement can use subagents for mechanical edits.

### Plan Agent

**You perform this phase.** Explore codebase, verify data contracts (trace every form field → API request → backend validation), identify all files to create/modify, design execution order. Do NOT write code. Output:

1. **Summary** — 1-2 sentences
2. **Files to Create / Modify** — path + what + why
3. **Docs to Update** — ROADMAP always, API-LAYOUT if endpoints change
4. **Data Contract Trace** — every field in every API request: where does it come from? Is it in the form?
5. **Execution Order** — numbered with dependencies
6. **Risks / Trade-offs**

Key rules:
- Trace every field from UI form → API payload. If the backend needs a field the form doesn't collect, you MUST add it to the form. Never assume empty defaults are safe.
- When async calls modify form state, specify the state update strategy (functional `setForm(prev => ...)`, stale-response guards).

### Frontend Agent (Implement)

**Subagent, or you directly for small changes.** React 18 + TypeScript + Vite 5 + CSS Modules. Follow these patterns:

**API calls**: Staff views import from `api/<module>.ts` (uses `request` interceptor — auto-injects token, unwraps `Result<T>`). Patient views use `patientRequest` from `api/patientRequest.ts` (auto-injects patientToken but does NOT unwrap response — keep `r.data.data.xxx`).

**UI patterns**: Modals: `<div className={styles.modalOverlay} onClick={close}>` + `<div className={styles.modal} onClick={e => e.stopPropagation()}>`. Forms: `<form className={styles.formGrid}>` with `<div className={styles.formGroup}>`. Tables: `<table className={styles.table}>`. Buttons: `btnPrimary` (save), `btnSm` (secondary), `btnSmDanger` (delete). CSS: `../shared.module.css` (staff), `../../shared.module.css` (patient).

**Falsy safety (CRITICAL)**: `!= null` for numeric checks, `?? '-'` for display fallback, `!== ''` for empty form checks. **NEVER use `||` for values that could be `0` or `false`.** `Number(x) || null` drops zero — use `x !== '' ? Number(x) : null`. `profile[f] || ''` drops 0/false — use `?? ''`.

**Common patterns**: Patient dropdowns: `getPatientPage({ page: 1, size: 999 })`. Delete confirmation: `confirm('Delete?')`. Simple input: `prompt('reason:')`. Pagination: `page*PAGE_SIZE>=total`. Import order: react → API modules → CSS → utils.

**Vite cache**: If changes don't appear, kill all vite processes and restart with `--force`.

### Backend Agent (Implement)

**Subagent, or you directly for small changes.** Java 17 + Spring Boot 3.x + Spring Data JPA. Follow these patterns:

**Controllers**: `@RestController` + `@RequestMapping("/api/v1/<module>")` + `@RequiredArgsConstructor`. Every endpoint: `@PreAuthorize("hasRole('ADMIN')")` / `hasAnyRole('ADMIN','DOCTOR')` / `hasRole('PATIENT')`. Return `Result<T>` or `Result<PageResult<T>>`. `@Valid` on request bodies. Controllers throw, never catch.

**Services**: `@Service` + `@RequiredArgsConstructor`. `@Transactional` on methods that modify data. `@Auditable(module, action)` on CUD operations. Ownership checks: `resource.getPatientId().equals(loginUser.getUserId())` for patient-owned resources.

**DTOs**: `@Data` + static `fromEntity()` / instance `toEntity()`. Conversion logic NEVER in controllers or services.

**Entities**: Extend `BaseEntity`. `@SQLDelete(sql = "UPDATE ... SET is_deleted = 1 WHERE id = ? AND version = ?")`. `@SQLRestriction("is_deleted = 0")`. `@Version` on critical entities. PHI: `@Convert(converter = AesAttributeConverter.class)` on entity fields, OR `AesCryptoUtil.encrypt()` in raw SQL — both valid.

**Errors**: `throw new BusinessException(ResultCode.X, "message")`.

### Review Agent

**You perform this phase.** Do NOT write code — only report findings. Run the full checklist:

**Backend checklist**: @PreAuthorize on every endpoint, patient-owned resources verify loginUser, DTO↔Entity in DTO class (not controller/service), Result<T> wrapper, @Valid on request bodies, PHI encryption, @Auditable on CUD, @Transactional on mutations, no raw SQL with user input, new entity extends BaseEntity with @SQLDelete/@SQLRestriction, @Version on critical entities.

**Frontend checklist**: No `||` on numeric values, form numeric fields use `!== ''` check, staff views use `api/` module (not raw axios), patient views use `patientRequest` or axios with `patientToken`, modal e.stopPropagation(), CSS from shared.module.css, no new npm imports, no commented-out code.

**Cross-cutting**: No new deps, file naming, cyclic refs, hardcoded creds. Both staff and patient logout clear their tokens.

**Severity**: 🔴 CRITICAL (security/data loss) / 🟡 HIGH (bug) / 🟠 MEDIUM (consistency) / ⚪ LOW (style). End with **VERDICT: Ready to merge** or **Blocked: N critical issues**.

**Recurring bug patterns to flag**:
- `|| null` on numbers → should be `!== '' ? Number(x) : null`
- `|| fallback` on display values where 0 is valid → should be `??`
- `.catch(() => {})` with no error state → user sees nothing on failure
- Raw axios in staff views → should use `api/` module
- Data contract mismatch: frontend sends hardcoded empty string for a field the backend needs → feature silently no-op
- Stale closure: `setForm({ ...form, items })` inside async callback → should be `setForm(prev => ...)`
