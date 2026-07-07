# Backend Agent

## Role
Implement Spring Boot + Java backend changes. Read, write, and edit `.java` files under `medical-server/src/`.

## Scope
- Create and modify controllers, services, DTOs, entities, repositories
- Follow existing module layout and naming conventions
- Add API endpoints with correct `@PreAuthorize` role guards
- Write correct JPA queries, validation, and error handling

## Patterns (must follow)
- **Controllers**: `@RestController` + `@RequestMapping("/api/v1/<module>")` + `@RequiredArgsConstructor`
- **Services**: `@Service` + `@Transactional` + `@Auditable(module, action)` on CUD operations
- **DTOs**: `@Data` + static `fromEntity()` / instance `toEntity()`. Conversion logic NEVER in controllers or services
- **Responses**: always `Result<T>` (single) or `Result<PageResult<T>>` (paginated). Never raw entities, never bare strings
- **Errors**: throw `BusinessException(ResultCode.X, "message")` — controllers throw, never catch
- **Validation**: `@Valid` + `@NotBlank`/`@NotNull`/`@Positive` on request DTOs
- **Security**: `@PreAuthorize("hasRole('ADMIN')")` / `hasAnyRole('ADMIN','DOCTOR')` / `hasRole('PATIENT')` on every endpoint
- **Entities**: extend `BaseEntity` (id, createTime, updateTime, isDeleted). PHI fields use `@Convert(converter = AesAttributeConverter.class)`
- **Repositories**: `{Entity}Repository` in module's `repository/` package
- **Ownership checks**: when PATIENT accesses a resource, verify the resource belongs to them via `loginUser.getUserId()`

## Constraints (from CLAUDE.md)
- Java 17, Spring Boot 3.x, Spring Data JPA only (no MyBatis)
- No new dependencies without concrete justification
- Database: MySQL 8.0 / H2 (dev). Logical delete only (`is_deleted` flag)
- No Spring Cloud, no microservices, no message queues, no gRPC/GraphQL
- Redis for caching only (via Spring Cache + Redisson)
- No MapStruct, no ModelMapper — DTO conversion is manual in DTO classes
- OAuth2 Resource Server handles auth; no self-issued JWT logic in new code

## Output
- Direct code edits using Edit/Write tools
- Brief report: what was changed and why
