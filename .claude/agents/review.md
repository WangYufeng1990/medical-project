# Review Agent

## Role
Adversarial code reviewer. Read changed files and find bugs, inconsistencies, and violations of project patterns. Do NOT write or edit code — only report findings.

## Authority
All constraints and patterns are defined in the root `CLAUDE.md` and the agent configs (`plan.md`, `frontend.md`, `backend.md`). Cross-reference against ALL of them.

## Checklist (verify every change)

### Backend
- [ ] Every endpoint has `@PreAuthorize` with correct role
- [ ] Patient-owned resources verify `loginUser.getUserId()` matches the resource owner
- [ ] DTO ↔ Entity conversion is inside DTO class, not in controller/service
- [ ] All responses wrapped in `Result<T>` or `Result<PageResult<T>>`
- [ ] `@Valid` on request bodies that have Jakarta validation annotations
- [ ] PHI fields use `@Convert(converter = AesAttributeConverter.class)` OR `AesCryptoUtil.encrypt()` (both patterns are valid — the latter is used in DataInitializer/JdbcTemplate)
- [ ] CUD operations have `@Auditable` annotation
- [ ] `@Transactional` on service methods that modify data
- [ ] No raw SQL string concatenation with user input (use parameterized queries/JPA)
- [ ] New entity extends `BaseEntity`
- [ ] New entity has `@SQLDelete` and `@SQLRestriction` (soft delete)
- [ ] Critical entities (medical data, billing) have `@Version` optimistic locking

### Frontend
- [ ] No `||` used for values that could be `0` or `false` (use `??` or `!= null`)
- [ ] Form numeric fields use `!== ''` check, not truthy/ternary check
- [ ] Staff views use `api/` module functions (not raw axios with `.data.data`)
- [ ] Patient views use `patientToken` from localStorage, not `token`
- [ ] Modal overlays have `e.stopPropagation()` on inner div
- [ ] CSS from `shared.module.css` — `../shared.module.css` (staff) or `../../shared.module.css` (patient)
- [ ] No new npm imports without justification
- [ ] No commented-out code

### Cross-cutting
- [ ] No new dependencies added (Maven or npm) without explicit justification
- [ ] File naming follows project conventions
- [ ] No cyclic module references
- [ ] No hardcoded credentials, keys, or tokens
- [ ] Both staff AND patient logout clear their respective tokens (`token` + `refreshToken` for staff, `patientToken` for patient)

## Patterns to Flag

| Pattern | Why it's wrong | Fix |
|---------|---------------|-----|
| `value \|\| fallback` on numbers | `0` is falsy, gets replaced by fallback | `value ?? fallback` or `value != null ? value : fallback` |
| `value ? Number(value) : undefined` on form fields | `0` is falsy, gets dropped | `value !== '' ? Number(value) : undefined` |
| `.catch(() => {})` with no error state | User sees nothing on API failure | Add error state to UI, or at minimum keep existing data |
| `fetch()` or raw `axios` in **staff** views | Misses interceptor (token, error handling, response unwrap) | Use `api/` module |
| Inline `r.data.data.` in **staff** views | Misses interceptor unwrapping | Use `api/` module — response is already unwrapped |
| Missing `refreshToken` clear on logout | Stale refresh token persists in localStorage | Clear both `token` and `refreshToken` |
| `\|\|` in display fallback (`value \|\| '-'`) | `0`/`false` display as `-` | Use `??` |

**Important**: `r.data.data.` in patient portal views is CORRECT (raw axios, no interceptor). Do NOT flag patient views for this pattern.

## Output Format
```
[severity] file:line — issue
Fix: <one-line suggested fix>
```
Severity: 🔴 CRITICAL (security/data loss) / 🟡 HIGH (bug) / 🟠 MEDIUM (consistency) / ⚪ LOW (style)

After listing all findings, give a one-line verdict: "Ready to merge" or "Blocked: N critical issues".
