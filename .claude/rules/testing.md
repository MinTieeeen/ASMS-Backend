# Testing

Target (NFR17): at least **70% coverage for business rules and authorization logic**. CI runs all tests on every pull request.

## 1. Test types

| Type | Tools | Scope | Location |
| --- | --- | --- | --- |
| Unit | JUnit 5, Mockito, AssertJ | Services, utilities, mappers | `src/test/java/com/asms/service/<module>/` |
| Web slice | `@WebMvcTest`, MockMvc, `spring-security-test` | Controllers: status codes, validation, ProblemDetail, 401/403 | `src/test/java/com/asms/controller/<module>/` |
| Integration | `@SpringBootTest`, `@Import(TestcontainersConfiguration.class)`, `@ActiveProfiles("test")` | Repositories on real PostgreSQL/Redis, full flows | mirrors the main package |

Integration tests need Docker running.

## 2. Naming and structure

- Test class: `<ClassUnderTest>Test` (unit/web) or `<ClassUnderTest>IT` (integration).
- Test method: `methodName_shouldExpectedBehavior_whenCondition`, e.g. `joinGroup_shouldThrowGroupFull_whenMemberLimitReached`.
- Reference requirement codes with `@DisplayName("BR02: rejects joining a full group")` when useful.
- Arrange - Act - Assert separated by blank lines; one behaviour per test.
- Use AssertJ (`assertThat`, `assertThatThrownBy`), not JUnit assertions.

## 3. Rules

- Every business rule (BRxx) and every row of the group permission matrix has at least one test, including the forbidden path.
- Time-dependent logic uses `Clock.fixed(...)`; tests never depend on the real current time or on execution order.
- Build test data with small factory/builder helpers; do not copy large setups between tests.
- Prefer Testcontainers over mocking repositories when testing queries.
- A bug fix comes with a test that fails before the fix.
