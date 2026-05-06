# Quality Guidelines

> Code quality standards, testing requirements, and forbidden patterns.

---

## Pre-Commit Requirements (Hard Gates)

Before claiming work is "done", all three must pass:

1. **Compile**: full rebuild with 0 errors (`mvn clean compile` or IDE build)
2. **Tests**: all unit tests green — every failure/error must be explained or fixed
3. **Warnings**: 0 warnings in IDE inspection (IntelliJ)

---

## Testing Standards

### What must be tested

- **Every Entity** must have a corresponding unit test
- Tests must cover:
  - **Happy path** — normal operation
  - **Boundary cases** — null inputs, empty collections, edge values
  - **Failure paths** — invalid state transitions, business rule violations

### Test framework

- **JUnit 5** (`org.junit.jupiter.api`)
- **AssertJ** for assertions (`org.assertj.core.api.Assertions.assertThat`)
- **Mockito** (via `spring-boot-starter-test`) for mocking
- **Spring Boot Test** for integration tests (`@SpringBootTest`)

### Test patterns from codebase

Unit test (plain JUnit, no Spring):
```java
// server/src/test/java/.../tracking/UnitOfWorkProviderTest.java
class UnitOfWorkProviderTest {

    @Test
    void shouldBuildWithBuilder() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder()
                .withIdentifier(TestEntity.class, e -> e.id)
                .build();
        assertNotNull(provider);
    }

    @Test
    void shouldReturnUnmodifiableExtractors() {
        UnitOfWorkProvider provider = UnitOfWorkProvider.builder().build();
        assertThrows(UnsupportedOperationException.class, () ->
                provider.getExtractors().put(Object.class, o -> o));
    }
}
```

Integration test (Spring context):
```java
// server/src/test/java/.../tenant/TenantRepositoryAspectTest.java
@SpringBootTest(classes = ProjectApplication.class)
class TenantRepositoryAspectTest {

    @Autowired
    private TestTenantNoteRepository tenantNoteRepository;

    @BeforeEach
    void setUpRequestScope() { /* init request scope */ }

    @AfterEach
    void tearDown() { /* cleanup */ }

    @Test
    void tenantScopedQueryShouldBeFilteredAndFailClosedWhenTenantMissing() {
        // test tenant isolation
    }
}
```

Key conventions:
- **Setup/teardown**: `@BeforeEach` / `@AfterEach` to isolate test state
- **Assertions**: AssertJ fluent style (`assertThat(x).isEqualTo(y)`, `assertThrows(...)`)
- **Test naming**: descriptive snake_case or camelCase describing the scenario
- **Cleanup**: always clean up test data to prevent cross-test pollution
- **No self-review**: tests must be reviewed by a different person/AI than the one who wrote the code

---

## Code Style

### Javadoc

- **Every class** must have Javadoc describing its purpose
- **Every method** (including non-public) must have Javadoc with `@param` and `@return` tags
- Keep it short: 1-2 lines
- Language: English for code keywords (`@param`, `@return`), Chinese for description text

```java
/**
 * 根据用户ID查询用户名称。
 * @param userId 用户唯一标识
 * @return 用户名称，非空返回
 */
String getUserName(Long userId);
```

### Inline Comments

- **Forbidden** — do not use `//` or `/* */` inside methods
- Exception: extremely complex logic (rare) or generic type explanations like `Map<String/* user id */, String/* user name */>`

### Variables

- Prefer `final` for all variables, parameters, and fields where possible

### Guard Clauses

- Use early returns instead of nested if-else:

```java
// Recommended
if (invalid) { return; }
// normal logic

// Forbidden
if (valid) {
    // nested logic
} else {
    // more nesting
}
```

### Ternary Operators

- Only for extremely simple expressions
- Prefer `Objects.requireNonNullElse()` and similar utilities over ternaries for null handling

### Parameter Formatting

- **< 4 parameters**: keep on one line (`method("a", "b", "c");`)
- **≥ 4 parameters**: consider wrapping or (better) refactoring to a parameter object

---

## Structural Rules

### Forbidden

- **Anonymous inner classes** — use lambdas instead
- **Inner classes or records inside Service classes**
- **SQL string concatenation** — always use parameterized queries / JPA / Criteria API
- **Hardcoded credentials** — all passwords/keys via IDE environment variables, never in git

### Required

- **Record for DTOs**: use `record` instead of `class` for data transfer objects
- **Factory for aggregate roots**: all root creation goes through a Factory class + `IDUtils.generateID()`
- **Java 21 features**: use Pattern Matching, Sequenced Collections, and other modern APIs where appropriate
- **DDD separation**: ACL (anti-corruption layer), PO, Entity must stay separate — no shortcuts

---

## DDD Pragmatic Rules

- **Reading** does NOT require creating a Domain Root — query directly when no mutation is involved
- **No CQRS** — the business complexity doesn't warrant it yet
- **No Event-based DDD** — same reason
- **`@Transactional`** goes on Application Service methods, not Domain layer

---

## Code Review Checklist

When reviewing code in this project, verify:

1. [ ] Javadoc on every class and method (including non-public)
2. [ ] No inline comments (unless truly exceptional)
3. [ ] `final` used where possible
4. [ ] Guard clauses, no deep nesting
5. [ ] DTOs use `record`
6. [ ] No anonymous inner classes
7. [ ] Factory + `IDUtils.generateID()` for aggregate creation
8. [ ] `BusinessException` for business errors (no checked exceptions)
9. [ ] Error handling via `ExceptionAdviser` (no try-catch in controllers)
10. [ ] Tests cover happy path, boundaries, and failures
11. [ ] No self-review (code author != test/review author)
12. [ ] 0 warnings in IDE
13. [ ] No hardcoded secrets/credentials
