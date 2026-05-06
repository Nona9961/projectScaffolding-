# Logging Guidelines

> Log levels, format, and per-layer logging conventions.

---

## Logging Stack

- **Implementation**: Log4j2 (logback is excluded in parent POM)
- **API**: SLF4J (via Lombok `@Slf4j` annotation)
- **Lombok**: `@Slf4j` on any class that needs logging — generates `log` field

Reference: `pom.xml:27-28` (log4j2 + disruptor for async logging)

---

## Log Levels and When to Use

| Level | When to use | Example |
|-------|-------------|---------|
| **ERROR** | Operation failed, needs attention | Unhandled exception, validation failures, DB errors |
| **WARN** | Degraded but functional | Retry succeeded, fallback used, slow query threshold |
| **INFO** | Key business events | Request completed, entity created/updated, config loaded |
| **DEBUG** | Diagnostic info for developers | SQL parameters, detailed state, BusinessAssert messages |
| **TRACE** | Very detailed tracing | Loop iterations, method entry/exit |

---

## Per-Layer Logging Rules

### Controller
- **Log**: request parameters, response summary, elapsed time
- **Level**: INFO for normal requests, WARN for client errors (4xx)

### Application Service
- **Log**: key business decisions, branch outcomes, cross-aggregate operations
- **Level**: INFO for business events, WARN for unexpected but handled cases

### Domain
- **Do NOT log** — communicate failures through `BusinessException`
- Exception: `BusinessAssert` logs at DEBUG level (conditionally) before throwing

### Repository / Infrastructure
- **Log**: slow queries (threshold TBD), connection issues
- **Level**: WARN for slow queries, ERROR for connection failures

---

## Current Codebase Patterns

All logging currently uses `log.error()` in `ExceptionAdviser`:

```java
// server/src/main/java/com/nona/application/advice/ExceptionAdviser.java
log.error("illegal args :{}", errors);
log.error("unhandled exception : {}", e.getMessage());
log.error("stack is ", e);  // exception as last arg → Log4j2 prints stacktrace
```

Pattern: **parameterized messages with `{}` placeholders** (SLF4J style), never string concatenation.

`BusinessAssert` uses conditional debug logging:
```java
// common/src/main/java/com/nona/util/BusinessAssert.java
if (log.isDebugEnabled()) {
    log.error(message);  // logged before throwing BusinessException
}
```

---

## Structured Logging

- Log4j2 supports structured/JSON layout — configure via `log4j2.xml` or `log4j2.yaml`
- Use parameterized messages: `log.info("user {} created order {}", userId, orderId)`
- Reserve structured (JSON) logging for production; plain text is fine for local dev

---

## What NOT to Log

- **Passwords, tokens, secrets** — never log credentials or API keys
- **PII** — phone numbers, email addresses, ID numbers
- **Full request bodies** in production — log summaries, not raw payloads
- **Stack traces to users** — log them server-side, return generic messages to clients

---

## Configuration

- Log4j2 configuration: `server/src/main/resources/log_config.xml`
- Async logging uses Disruptor (declared in POM: `disruptor.version=3.4.4`)
