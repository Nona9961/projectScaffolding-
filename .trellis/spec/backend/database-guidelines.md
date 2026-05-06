# Database Guidelines

> ORM patterns, persistence conventions, and query rules for this project.

---

## ORM & Persistence Stack

- **JPA/Hibernate** via Spring Data JPA (`spring-boot-starter-data-jpa`)
- **Spring Boot 4.0.3** with `@EnableJpaRepositories` scanning `com.nona.inf.persistence.repository.jpa`
- **Log4j2** for logging (logback excluded via POM)
- **No MyBatis usage** despite being declared in parent POM dependency management

Reference: `server/src/main/java/com/nona/ProjectApplication.java:8`

---

## PO (Persistent Object) Hierarchy

### BasePO — all RDB entities

```java
// server/src/main/java/com/nona/inf/persistence/po/BasePO.java
@Data
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class BasePO {
    @Id
    protected Long id;
    @Column(nullable = false, updatable = false)
    @CreatedDate
    protected LocalDateTime createTime;
    @Column(nullable = false)
    @LastModifiedDate
    protected LocalDateTime updateTime;
}
```

Key conventions:
- `@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)` — each concrete PO gets its own table with all columns
- `Long` as ID type for all entities
- `LocalDateTime` for timestamps, ISO 8601
- `@CreatedDate` / `@LastModifiedDate` — audit fields on every table

### TenantScopedBasePO — multi-tenant entities

```java
// server/src/main/java/com/nona/inf/persistence/po/TenantScopedBasePO.java
@Data
@EqualsAndHashCode(callSuper = true)
@Entity
public abstract class TenantScopedBasePO extends BasePO {
    @Column(nullable = false, length = 64, name = "tenant_id")
    @TenantId
    protected String tenantID;
}
```

When to use each:
- **`TenantScopedBasePO`**: data isolated per tenant (most business entities)
- **`BasePO`**: global/shared data not tied to any tenant

---

## Repository Pattern

### Domain-side interface (common module)

```java
// common/src/main/java/com/nona/persistence/BaseRepository.java
public interface BaseRepository<ID, Root> {
    Root getByID(ID id);
    boolean save(Root domain);
    int delete(Root domain);
    int deleteByID(ID id);
}
```

### Implementation — DifferRepository (change-tracking)

```java
// server/src/main/java/com/nona/inf/persistence/repository/DifferRepository.java
@RequiredArgsConstructor
public abstract class DifferRepository<Root, PO extends BasePO, Other>
        implements BaseRepository<Long, Root> {

    protected final ListCrudRepository<PO, Long> repository;  // Spring Data JPA
    protected final ThreadContext threadContext;
    protected final RdbGeneralConvertor<Root, PO, Other> convertor;
    protected final UnitOfWorkProvider unitOfWorkProvider;
    // ...
}
```

Repository flow:
1. **Read**: `repository.findById(id)` → `convertor.convertToRoot(po, other)` → register in UnitOfWork for tracking
2. **Save (new)**: `doInsert(root)` (subclass decides JDBC/JPA) → register clean snapshot
3. **Save (update)**: `uow.calculateChanges()` → `doUpdate(root, changeSet)` → re-register snapshot

### JPA sub-repositories

Spring Data JPA repositories extending `ListCrudRepository` are placed under (in test):
```
server/src/test/java/com/nona/inf/persistence/repository/jpa/
```

In main source, `DifferRepository` lives directly under `inf/persistence/repository/`; dedicated JPA sub-repositories for production entities have not yet been created.

---

## DO ↔ PO Conversion

```java
// server/src/main/java/com/nona/inf/persistence/converters/PoConverter.java
public interface PoConverter<DO, PO> {
    Class<DO> domainClass();
    Class<PO> poClass();
    PO toPO(DO domain);
    DO toDomain(PO po);
}
```

- `RdbGeneralConvertor` handles composite conversion (root + optional "other" objects)
- `ConverterRegistry` manages converter lookup by DO/PO class
- `CompositePoConverter` chains multiple converters for complex aggregates

---

## Multi-Tenancy

- **Hibernate-level filter**: `HibernateMultiTenancyConfig` + `ThreadContextTenantIdentifierResolver`
- **Tenant ID source**: `ThreadContext.getTenantID()` (thread-local, set per request)
- **AOP enforcement**: `TenantRepositoryAspect` injects tenant ID on write, filters on read
- **Cross-tenant bypass**: `@CrossTenant` annotation + `CrossTenantAspect` temporarily clears tenant filter (scope-bound — restored after method exit)
- **Fail-closed**: missing or blank tenant → queries return empty, writes throw `BusinessException`

Reference: `server/src/main/java/com/nona/inf/persistence/tenant/`

---

## ID Generation

```java
// common/src/main/java/com/nona/util/IDUtils.java
public class IDUtils {
    private static final Sequence sequence = new Sequence(1, 1);
    public static Long generateID() {
        return sequence.nextId();
    }
}
```

- Snowflake-style via `Sequence` class (`common/src/main/java/com/nona/persistence/Sequence.java`)
- Always use `IDUtils.generateID()`, never manual ID assignment

---

## Transaction Management

- `@Transactional` belongs on **Application Service layer** methods (per shared standards)
- Domain layer does NOT reference transactions
- Cross-aggregate operations are orchestrated in Service layer within a single transaction

Note: `@Transactional` is not yet present in the current codebase — the project is early stage.

---

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Table name | Derived from PO class (JPA default, TABLE_PER_CLASS) | `BasePO` subclasses |
| Column name | camelCase in Java, snake_case in DB (JPA default) | `create_time`, `tenant_id` |
| Entity ID column | `id` (Long) | |
| Tenant column | `tenant_id` (String, length 64) | |
| Audit columns | `create_time`, `update_time` (LocalDateTime) | |

---

## Forbidden Patterns

- **SQL string concatenation** — always use parameterized queries / JPA / Criteria API
- **Manual ID assignment** — always use `IDUtils.generateID()`
- **Direct PO exposure** to domain or API layers — always convert through `PoConverter`
- **Tenant ID hardcoding** — always read from `ThreadContext`
