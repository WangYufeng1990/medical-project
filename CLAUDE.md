# CLAUDE.md — Medical Management System (Backend)

## Project Overview

A Spring Boot backend for a frontend-backend separated medical management system. Provides RESTful APIs consumed by a separate frontend application.

---

## Tech Stack (Mandatory)

| Layer | Technology | Version / Note |
|-------|-----------|---------------|
| Language | Java | 17 (LTS) |
| Framework | Spring Boot | 3.x (latest stable) |
| Build Tool | Maven | wrapper included |
| ORM | MyBatis-Plus | 3.5+ |
| Database | MySQL | 8.0+ |
| Cache | Redis | 7.x, accessed via Spring Cache + Redisson |
| Auth | Spring Security + JWT | stateless, token-based |
| API Doc | Knife4j (Swagger wrapper) | latest |
| Validation | Jakarta Validation + Hibernate Validator | bundled with Spring Boot |
| JSON | Jackson | bundled with Spring Boot |
| Util | Lombok, Hutool | latest |
| Testing | JUnit 5 + Spring Boot Test | bundled |

**Explicitly excluded (DO NOT introduce):**
- No JPA/Hibernate (MyBatis-Plus is the single ORM)
- No Spring Cloud / microservices (this is a monolithic backend)
- No gRPC, GraphQL (REST only)
- No Elasticsearch, MongoDB, Neo4j (MySQL + Redis only)
- No message queues (RabbitMQ, Kafka, RocketMQ) unless a concrete async requirement arises
- No MapStruct, ModelMapper or any object-mapping library. DTO ↔ Entity conversion logic MUST be encapsulated inside DTO classes (static factory `fromEntity()` / instance `toEntity()` methods). Never scatter conversion code in Controllers or Services.
- No Shiro (Spring Security is the single auth framework)

---

## Directory Structure Convention

```
src/
└── main/
    ├── java/com/example/medical/
    │   ├── MedicalApplication.java          # entry point
    │   ├── common/                          # shared infrastructure
    │   │   ├── config/                      # @Configuration classes
    │   │   ├── exception/                   # GlobalExceptionHandler + custom exceptions
    │   │   ├── result/                      # unified Result<T> response wrapper
    │   │   ├── base/                        # BaseEntity, BaseController (shared fields/methods)
    │   │   └── enums/                       # shared enums (ResultCode, etc.)
    │   ├── module/                          # ---- business modules ----
    │   │   ├── system/                      #   system management (users, roles, menus)
    │   │   │   ├── controller/
    │   │   │   ├── service/  + impl/
    │   │   │   ├── mapper/
    │   │   │   ├── entity/
    │   │   │   └── dto/
    │   │   ├── patient/                     #   patient records
    │   │   ├── appointment/                 #   appointments / scheduling
    │   │   ├── prescription/                #   prescriptions
    │   │   ├── billing/                     #   billing & payments
    │   │   └── ...                          #   add modules as needed
    │   └── util/                            # general-purpose helper classes
    └── resources/
        ├── application.yml                  # default config
        ├── application-dev.yml              # dev profile
        ├── application-prod.yml             # prod profile
        └── mapper/                          # MyBatis XML mappings (if any)
```

**Rules:**
- Every business module lives under `module/<module-name>/` with an identical internal layout.
- `common/` is strictly for cross-cutting concerns. Business logic goes into modules.
- No cyclic references between modules. If two modules need the same thing, it belongs in `common/`.
- DTOs go in the module's own `dto/` directory, not a global one.
- All custom MyBatis XML files MUST live under `src/main/resources/mapper/`. File names must match Mapper interface names exactly (`PatientMapper.xml` ↔ `PatientMapper.java`). No subdirectories. This prevents Maven from silently excluding them from the build artifact.

---

## Development Principles (Non-Negotiable)

### 1. Zero Fluff
- **No docstrings or comments on code that is self-explanatory.** A method named `findPatientById` does not need a Javadoc explaining "Finds a patient by ID."
- A comment is only justified when the WHY is non-obvious — a subtle invariant, a deliberate workaround, a constraint dictated by an upstream system.
- Do not write "implementation plan" documents, README files, or any documentation unless explicitly asked.

### 2. Dependency Discipline
- **Do not add a dependency unless it solves a real, concrete problem.**
- Always prefer the standard library, Spring Boot built-ins, or the stack already listed above before reaching for a new library.
- If a new dependency seems necessary, state the justification and ask for approval.
- No "just in case" or "we might need it later" dependencies.

### 3. Code Generation Discipline
- **Do not generate boilerplate code proactively.** Wait for a concrete request from the user before creating entities, controllers, services, mappers, or DTOs.
- When asked to implement something, generate only the files directly needed — no extra "helper" classes, no speculative abstractions, no half-finished stubs.
- Three similar lines of code is better than a premature abstraction.

### 4. Error Handling
- Use a global `@RestControllerAdvice` handler. Controllers should throw, not catch.
- Return `Result<T>` for all API responses — never raw entities, never bare strings.
- Business exceptions extend a single `BusinessException(baseCode, message)`.

### 5. API Design
- All endpoints return `Result<T>`: `{ "code": 200, "message": "ok", "data": {...} }`
- HTTP verbs strictly by semantics: GET for reads, POST for creates, PUT for full updates, PATCH for partial updates, DELETE for deletes.
- Path naming: `/api/v1/<module>/<resource>`, e.g., `/api/v1/patients/{id}`
- Paging parameters use a shared `PageQuery` base class.
- All paginated endpoints MUST return `Result<PageResult<T>>`. `PageResult<T>` is a single standardized wrapper containing `total`, `size`, `current`, and `records`. Never leak raw MyBatis-Plus `Page` objects into the API response.

### 6. Security
- Stateless JWT authentication. Every request carries `Authorization: Bearer <token>`.
- Passwords are hashed with BCrypt.
- Medical data fields requiring AES encryption MUST use MyBatis-Plus `@TableField(typeHandler = AesTypeHandler.class)` or a registered MyBatis interceptor configured in `common/config/`. NEVER write manual encrypt/decrypt wrappers in Service classes — encryption must be transparent to business logic.
- Role-based access control: `@PreAuthorize("hasRole('ADMIN')")` on controller methods.

### 7. Database
- All tables MUST have `id`, `create_time`, `update_time` columns — inherited from `BaseEntity`.
- Logical delete only (`is_deleted` flag), never physical DELETE.
- Index foreign keys and frequently queried columns.
- Medical data integrity: use `@Version` optimistic locking on critical entities.

### 8. Testing
- Write tests only when asked.
- When asked, cover: the happy path, one edge case, one failure mode. No more.
- Use `@WebMvcTest` for controllers, `@MybatisPlusTest` for mappers.

### 9. Git
- Do not initialize git or make commits unless explicitly asked.
- When asked to commit: small, focused commits with messages in English present tense.

### 10. Response Style
- Be concise. State what you are about to do in one sentence, do it, report the result.
- No summaries, no "great question!", no emojis, no markdown tables unless data comparison requires it.
- If you hit a blocker or ambiguity, ask — don't guess.
