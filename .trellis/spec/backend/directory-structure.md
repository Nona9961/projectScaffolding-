# Directory Structure

> Backend module organization and file layout conventions.

---

## Module Layout

```
backend/
├── api/           # Public API contracts & DTOs exposed to consumers
├── common/        # Shared utilities, exceptions, base abstractions
├── server/        # Main application (DDD layers + infrastructure)
└── pom.xml        # Parent POM (Spring Boot 4.0, Java 21)
```

| Module | Purpose | Depends on |
|--------|---------|------------|
| `api` | `PublicApi` interface + DTO records consumed by external callers | `common` |
| `common` | `BusinessException`, `BusinessAssert`, `HttpResponse`, `BaseRepository`, `IDUtils`, event bus | nothing |
| `server` | Application entry point, DDD domain, infrastructure | `api`, `common`, `change-tracking-api` |

---

## Server DDD Layer Layout

```
server/src/main/java/com/nona/
├── ProjectApplication.java       # @SpringBootApplication entry point
├── application/
│   └── advice/
│       └── ExceptionAdviser.java # @RestControllerAdvice global error handler
├── controller/                   # REST controllers (presentation layer)
├── domain/
│   └── <aggregate>/
│       ├── entity/               # Aggregate root, value objects, enums
│       │   ├── DomainRoot.java
│       │   ├── DomainValueObject.java
│       │   └── SomeStatus.java
│       ├── factory/              # Factory for aggregate creation
│       │   └── DomainRootFactory.java
│       ├── ports/                # Interfaces to external services (ACL)
│       │   └── bizRelated/
│       │       └── BizClient.java
│       └── repo/                 # Repository interfaces (domain-side contract)
│           └── DomainRootRepository.java
└── inf/
    ├── context/                  # Cross-cutting context (tenant, request)
    │   ├── CrossTenant.java
    │   ├── TenantContextAccessor.java
    │   └── ThreadContext.java
    └── persistence/
        ├── po/                   # JPA entity PO classes
        │   ├── BasePO.java
        │   └── TenantScopedBasePO.java
        ├── converters/           # DO ↔ PO converters
        │   ├── PoConverter.java
        │   ├── AbstractConvertor.java
        │   ├── CompositePoConverter.java
        │   ├── ConverterRegistry.java
        │   └── RdbGeneralConvertor.java
        ├── repository/           # Repository implementations
        │   └── DifferRepository.java
        ├── tracking/             # UnitOfWork / change tracking
        ├── tenant/               # Multi-tenancy: Hibernate config, aspects
        ├── applier/              # Change appliers
        ├── dispatcher/           # Change dispatchers
        └── reconstructor/        # PO reconstruction from diff
```

---

## Module Organization Rules

- **One aggregate per package** under `domain/`. Package name = aggregate name (lowerCamelCase).
- **Entity classes** go in `entity/` sub-package: root, value objects, enums all live together.
- **Factory classes** go in `factory/` sub-package. All aggregate root creation must go through a Factory.
- **Repository interfaces** (domain-side) go in `repo/` — extend `BaseRepository<ID, Root>` from common.
- **Repository implementations** go in `inf/persistence/repository/`.
- **PO classes** go in `inf/persistence/po/` — extend `BasePO` or `TenantScopedBasePO`.
- **Converters** (DO ↔ PO mapping) go in `inf/persistence/converters/` — implement `PoConverter<DO, PO>`.
- **External service interfaces** (ACL) go in `domain/<aggregate>/ports/`.

---

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Aggregate package | lowerCamelCase | `rootPackage`, `anotherRootPackage` |
| Entity class | Noun, no suffix | `DomainRoot` |
| Value object | Noun, no suffix | `DomainValueObject` |
| Enum | Noun/Adjective | `SomeStatus` |
| Factory | `*Factory` | `DomainRootFactory` |
| Repository interface | `*Repository` | `DomainRootRepository` |
| Repository impl | `*Repository` or `Differ*Repository` | `DifferRepository` |
| PO class | `*PO` | `BasePO`, `TenantScopedBasePO` |
| Converter | `*Converter` or `*Convertor` | `PoConverter`, `RdbGeneralConvertor` |
| Controller | `*Controller` | (not yet present) |
| Test class | `*Test` | `UnitOfWorkProviderTest` |

---

## Test Layout

```
server/src/test/java/com/nona/
└── inf/persistence/
    ├── applier/              # Change applier tests
    ├── converters/           # Converter unit tests
    ├── integration/          # Full integration tests
    ├── reconstructor/        # Reconstructor tests
    ├── repository/jpa/       # JPA repository tests
    ├── tenant/               # Tenant isolation tests
    └── tracking/             # UnitOfWork / tracking tests
```

Tests mirror the main source package structure under the same module's `src/test/java/`.
