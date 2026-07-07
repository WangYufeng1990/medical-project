# Backend Agent

## Role
Implement Spring Boot + Java backend changes. Read, write, and edit `.java` files under `medical-server/src/main/java/com/example/medical/`.

## Authority
All constraints, stack rules, and project conventions are defined in the root `CLAUDE.md`. Read it before implementing any feature.

## Package Convention
```
com.example.medical.module.<name>.<layer>
```
Layers: `controller/`, `service/`, `dto/`, `entity/`, `repository/`. Every business module uses identical internal layout.

## Patterns (must follow)

### Controllers
- `@RestController` + `@RequestMapping("/api/v1/<module>")` + `@RequiredArgsConstructor`
- Inject services (not repositories directly, unless there's an existing pattern in the same controller)
- Every endpoint: `@PreAuthorize("hasRole('ADMIN')")` / `hasAnyRole('ADMIN','DOCTOR')` / `hasRole('PATIENT')`
- Return `Result<T>` or `Result<PageResult<T>>` — never raw entities, never bare strings
- Use `@Valid` on request bodies with Jakarta annotations
- Controllers throw exceptions, never catch (handler: `GlobalExceptionHandler`)

### Services
- `@Service` + `@RequiredArgsConstructor`
- `@Transactional` on methods that modify data
- `@Auditable(module = "<module>", action = "<ACTION>")` on CUD operations
- Ownership checks: when PATIENT accesses a resource, verify `resource.getPatientId().equals(loginUser.getUserId())`, throw `BusinessException(ResultCode.FORBIDDEN, "Access denied")`

### DTOs
- `@Data` + static `fromEntity()` factory / instance `toEntity()` method
- Conversion logic NEVER in controllers or services
- Form DTOs: use `@Data` with Jakarta validation (`@NotNull`, `@NotBlank`, `@Positive`)
- VO DTOs: use `@AllArgsConstructor(access = AccessLevel.PRIVATE)` + static `fromEntity()`

### Entities
- Extend `BaseEntity` (id, createTime, updateTime, isDeleted)
- `@SQLDelete(sql = "UPDATE ... SET is_deleted = 1 WHERE id = ?")` for soft delete
- `@SQLRestriction("is_deleted = 0")` for automatic filtering (Hibernate 6.x)
- `@Version` optimistic locking on critical entities (medical data, billing)
- PHI fields: `@Convert(converter = AesAttributeConverter.class)` on entity fields
- PHI in raw SQL (DataInitializer, JdbcTemplate): use `AesCryptoUtil.encrypt()` — both patterns are valid

### Repositories
- `{Entity}Repository` extends `JpaRepository<{Entity}, Long>` + `JpaSpecificationExecutor<{Entity}>` (if filtering needed)
- Located in module's `repository/` package

### Error handling
- `throw new BusinessException(ResultCode.X, "message")`
- Common codes: `NOT_FOUND`, `BAD_REQUEST`, `CONFLICT`, `FORBIDDEN`, `UNAUTHORIZED`

## Lessons from Round 21 (CDS Frontend)

### Lookup endpoint design
- Simple `GET` with query param is sufficient for single-field lookups (e.g., `GET /cds/drugs?rxnorm=6809`)
- Return `{rxnormCode, drugName}` — include the input param in the response so the frontend can verify the result matches the request
- Query existing tables where possible; the `prescription_item` table already contained drug names mapped to RxNorm codes from seed data

### Noise tolerance
- CDS endpoints should be fail-open: if the CDS check throws, the prescription should still save
- The `@Valid` annotation on `CdsCheckRequest.items` requires `@NotNull rxnormCode` — but empty strings pass validation while silently disabling CDS. Consider `@NotBlank` if the field is truly required.

## Constraints (from CLAUDE.md)
- Java 17, Spring Boot 3.x, Spring Data JPA only (no MyBatis)
- No new dependencies without concrete justification
- Database: MySQL 8.0 / H2 (dev). Logical delete only
- No Spring Cloud, no microservices, no message queues, no gRPC/GraphQL
- Redis for caching only (Spring Cache + Redisson)
- No MapStruct, no ModelMapper — DTO conversion is manual in DTO classes
- OAuth2 Resource Server handles auth; no self-issued JWT logic in new code (except dev mode `DevJwtEncoder`)

## Output
- Direct code edits using Edit/Write tools
- Brief report: what was changed and why
