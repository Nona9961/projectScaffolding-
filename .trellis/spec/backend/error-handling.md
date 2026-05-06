# Error Handling

> Error types, propagation, and API response conventions.

---

## Exception Hierarchy

### BusinessException — the only business exception

```java
// common/src/main/java/com/nona/exceptions/BusinessException.java
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
```

- **Unchecked** (extends `RuntimeException`) — no checked exception propagation
- **Message is user-facing** — write messages that make sense to API consumers
- Domain layer throws `BusinessException` for business rule violations

---

## Global Exception Handler

```java
// server/src/main/java/com/nona/application/advice/ExceptionAdviser.java
@RestControllerAdvice
@Slf4j
public class ExceptionAdviser {

    @ExceptionHandler(BusinessException.class)
    public HttpResponse<?> handleBusinessException(BusinessException e) {
        return HttpResponse.fail(e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public HttpResponse<?> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage()));
        log.error("illegal args :{}", errors);
        return HttpResponse.fail(errors);
    }

    @ExceptionHandler(BindException.class)
    public HttpResponse<?> handleBindException(BindException ex) {
        // Same pattern as MethodArgumentNotValidException
    }

    @ExceptionHandler(RuntimeException.class)
    public HttpResponse<?> handleRuntimeException(RuntimeException e) {
        log.error("unhandled exception : {}", e.getMessage());
        log.error("stack is ", e);
        return HttpResponse.fail("Severe internal error. Please retry later.");
    }
}
```

Key rules:
- **All handlers return `HttpResponse`** — HTTP 200 with business-level error code
- **`BusinessException`** → message passed through to client
- **Validation exceptions** → field-level error map returned
- **`RuntimeException`** (catch-all) → generic message, stack trace logged, internal details NOT leaked

---

## HTTP Response Format

```java
// common/src/main/java/com/nona/api/HttpResponse.java
public record HttpResponse<T>(int code, String message, boolean success, T data) {

    public static final int SUCCESS_CODE = 0;
    public static final int UNAUTHORIZED_CODE = 401;
    public static final int FAIL_CODE = 500;
    // ...
}
```

| Scenario | code | success | Example message |
|----------|------|---------|-----------------|
| Success | `0` | `true` | `"success"` |
| Business error | `500` | `false` | BusinessException message |
| Validation error | `500` | `false` | Field error map |
| Internal error | `500` | `false` | `"Severe internal error. Please retry later."` |
| Unauthorized | `401` | `false` | `"unauthorized"` |

All responses return **HTTP 200** (default for `@RestControllerAdvice` without `@ResponseStatus`) — errors are signaled by `code` + `success` fields in the response body, not by HTTP status codes.

---

## Validation & Assertions

### BusinessAssert — domain guard clauses

```java
// common/src/main/java/com/nona/util/BusinessAssert.java
@Slf4j
public class BusinessAssert {

    public static void assertNonNull(Object object, String message, Object... args) {
        assertTrue(Objects.nonNull(object), message, args);
    }

    public static void assertTrue(boolean condition, String message, Object... args) {
        if (!condition) {
            throwBusiness(message, args);
        }
    }

    public static void throwBusiness(String message, Object... args) {
        throw generateExByMsg(message, args);
    }
}
```

Key patterns:
- **Message templates** use SLF4J-style `{}` placeholders with `MessageFormatter.arrayFormat()`
- **`assertTrue`** for boolean conditions; **`assertNonNull`** for null checks
- All assertions throw `BusinessException` (never return error codes)
- At debug log level, the message is also logged before throwing

### Layered Validation Strategy

| Layer | What to validate | Tool |
|-------|-----------------|------|
| **Controller** | Format/input validation | `@Valid`, `@NotBlank` etc. (JSR-380) |
| **Domain** | Business rules | `BusinessAssert` |
| **Repository** | Trust DB constraints | No redundant checks |

---

## Error Propagation Rules

1. **Domain layer** throws `BusinessException` when business rules are violated
2. **Application/Service layer** may catch, add context, re-throw — but never swallow
3. **Controller layer** does NOT try-catch — let `ExceptionAdviser` handle all
4. **Never** catch an exception and return null or do nothing

---

## Null Handling

- **Prefer non-null returns**: return `Collections.emptyList()`, `Optional`, or use null-object pattern
- **Trust DB constraints**: if a column is `NOT NULL`, don't re-check after reading from DB
- **Don't blindly null-check**: only check null where the business/contract doesn't guarantee non-null
- `DifferRepository.getByID()` returns `null` when entity not found (caller handles)

---

## Forbidden Patterns

- **Swallowing exceptions**: catch block that logs and returns null/empty without re-throwing
- **Checked exceptions**: use `BusinessException` (unchecked) exclusively
- **Returning error codes**: use exceptions, never `Result<T, E>` or error-code enums
- **Leaking stack traces to clients**: always mask internal errors behind generic messages
