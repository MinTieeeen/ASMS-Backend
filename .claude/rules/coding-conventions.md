# Coding Conventions

## 1. Naming

| Element | Convention | Example |
| --- | --- | --- |
| Package | lowercase, singular | `com.asms.service.group` |
| Class / interface / enum / record | `PascalCase` noun | `GroupService`, `TaskStatus` |
| Method | `camelCase` verb | `transferLeadership`, `approveTask` |
| Variable / field | `camelCase` | `maxMembers` |
| Constant | `UPPER_SNAKE_CASE` | `MAX_DEPUTIES` |
| Boolean | `is/has/can/should` prefix | `isArchived`, `canApprove` |

Role suffixes: `XxxController`, `XxxService`, `XxxRepository`, `XxxMapper`, `XxxConfig`, `XxxProperties`, `XxxException`, `XxxEvent`, `XxxListener`, `XxxScheduler`.
DTOs: `CreateXxxRequest`, `UpdateXxxRequest`, `XxxResponse`, `XxxSummaryResponse` (list items), `XxxFilter` (query parameters).
Entities have **no** suffix (`Group`, `Task`). Enums describe a value set (`GroupRole`, `TaskPriority`).
Service methods use business verbs: `createGroup`, `closePhase`, `submitDeliverable`. Avoid vague names such as `process`, `handle`, `doWork`, `manage`, `data`, `info`.

## 2. Spring

- Constructor injection only: `private final` fields + Lombok `@RequiredArgsConstructor`. Field injection (`@Autowired` on fields) is forbidden.
- `@Bean` methods are package-private unless they must be public.
- `@Transactional` belongs to service methods; use `@Transactional(readOnly = true)` for reads. Never on controllers or repositories.
- Business settings go into `AppProperties` (`app.*`), not scattered `@Value`.
- Current time comes from the injected `Clock` bean - never `Instant.now()` / `LocalDate.now()` without a clock.
- Nullability: use JSpecify `@Nullable` (`org.jspecify.annotations`), not the deprecated `org.springframework.lang` annotations.

## 3. Lombok

- Entities: `@Getter`, `@Setter` (or explicit domain methods), `@NoArgsConstructor(access = AccessLevel.PROTECTED)`.
- **Forbidden on entities:** `@Data`, `@EqualsAndHashCode`, `@ToString`, `@AllArgsConstructor` (lazy loading and recursion bugs).
- Services/components: `@RequiredArgsConstructor`, `@Slf4j`.
- No Lombok on DTOs - they are `record`s.

## 4. JPA

- Every entity extends `BaseEntity` (or `SoftDeletableEntity` + `@SQLRestriction("deleted_at IS NULL")`).
- Associations are `fetch = FetchType.LAZY`. Avoid N+1 queries with `@EntityGraph` or `JOIN FETCH`.
- Enums are stored with `@Enumerated(EnumType.STRING)`.
- No `CascadeType.ALL` / `orphanRemoval` unless the child truly belongs to the parent aggregate.
- Prefer projections or DTO queries for read-heavy lists (dashboard, admin tables).

## 5. Clean code

- One responsibility per class and per method. Split a method above ~30 lines or nested more than 2 levels.
- Guard clauses and early returns instead of deep `if/else`.
- No magic numbers or strings: use constants, enums or `AppProperties`.
- Never return `null` for collections (return an empty list). Use `Optional` only as a return type for single lookups.
- Throw `BusinessException(ErrorCode.X)` (or a subclass) for business errors; never a bare `RuntimeException`, never swallow exceptions.
- Prefer immutability: `final` fields, records, `List.of`, `Stream.toList()`.
- Use Java 21 features where they make code clearer: records, switch expressions, pattern matching, text blocks.
- No duplicated logic: extract to a private method, a shared service or `util/`.
- Log with `@Slf4j` placeholders: `log.info("User {} joined group {}", userId, groupId)`. Never log passwords, tokens or peer review content. `ERROR` only for unexpected failures, `WARN` for recoverable anomalies, `DEBUG` for diagnostics.

## 6. Formatting

Spotless with Palantir Java Format decides the layout (4-space indent, 120 columns, import order). Run `./mvnw spotless:apply`; do not hand-format. Wildcard and unused imports are not allowed.
