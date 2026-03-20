# DDD-RDB 阻抗失衡解决方案

> **更新时间**：2025-12-28
> **项目状态**：✅ 架构完成

---

## 一、问题分析

### 1.1 什么是阻抗失衡

DDD 领域模型与关系型数据库之间存在本质的结构差异：

| 维度 | DDD 领域模型 | 关系型数据库 |
|------|-------------|-------------|
| **数据结构** | 对象图（树形/网状） | 二维表（扁平） |
| **标识** | 实体有标识，值对象无标识 | 每行必须有主键 |
| **关系** | 对象引用、聚合边界 | 外键关联 |
| **封装** | 行为与数据封装在一起 | 纯数据存储 |

### 1.2 具体问题场景

```
聚合根 Order
├── id: Long
├── status: OrderStatus (枚举)
├── customer: Customer (值对象)
│   ├── name: String
│   └── phone: String
└── items: List<OrderItem> (值对象集合)
    ├── productId: Long
    ├── quantity: int
    └── price: Money (嵌套值对象)
```

**问题1：值对象持久化** - `Customer` 是值对象，无标识，存储方式？
**问题2：集合映射** - `items` 需要独立表，但值对象本身无 ID
**问题3：变更追踪** - 修改 `items[0].quantity` 时，如何知道要更新哪条记录？
**问题4：聚合一致性** - 保存聚合根时，需要同时操作多张表

---

## 二、解决方案

### 2.1 采用方案：change-tracking 库

基于 **Unit of Work 模式** 的变更追踪库，提供自动化的对象图变更追踪能力。

```xml
<dependency>
    <groupId>com.nona</groupId>
    <artifactId>change-tracking-api</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

### 2.2 核心 API

```java
// 1. 创建 UnitOfWork
UnitOfWork uow = UnitOfWorkFactory.builder().withDefaults().build();

// 2. 注册要追踪的对象（创建初始快照）
uow.registerClean(aggregateRoot);

// 3. 业务逻辑修改对象
aggregateRoot.setStatus(OrderStatus.PAID);
aggregateRoot.getItems().add(newItem);

// 4. 计算变更
ChangeSet changeSet = uow.calculateChanges();

// 5. 处理变更（用于持久化）
for (Change change : changeSet.getLeafChanges()) {
    switch (change) {
        case FieldChange fc -> handleFieldChange(fc.path(), fc.oldValue(), fc.newValue());
        case ItemAddedChange iac -> handleItemAdded(iac.path(), iac.addedItem());
        case ItemRemovedChange irc -> handleItemRemoved(irc.path(), irc.removedItem());
        default -> {}
    }
}
```

---

## 三、整体架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Application Layer                        │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      Domain Layer                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐  │
│  │ AggregateRoot│  │ ValueObject │  │ Repository (接口)    │  │
│  └─────────────┘  └─────────────┘  └─────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                  Infrastructure Layer                        │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 DifferRepository                       │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │ save(root):                                      │  │  │
│  │  │   1. changeSet = uow.calculateChanges()          │  │  │
│  │  │   2. doUpdate(root, changeSet) // 子类实现       │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### 核心组件

| 组件 | 位置 | 职责 |
|------|------|------|
| `UnitOfWork` | change-tracking 库 | 快照存储、变更计算 |
| `DifferRepository` | `inf/persistence/repository/` | 抽象仓储基类，集成 UnitOfWork |
| `ChangeDispatcher` | `inf/persistence/dispatcher/` | 变更分发（主表/子表分类） |
| `PoReconstructor` | `inf/persistence/reconstructor/` | 从 Root+ChangeSet 重建 PO |
| `ConverterRegistry` | `inf/persistence/converters/` | 转换器注册中心 |

---

## 四、持久化策略

### 4.1 DifferRepository 接口

```java
public abstract class DifferRepository<Root, PO extends BasePO, Other>
    implements BaseRepository<Long, Root> {

    @Override
    public boolean save(Root root) {
        ChangeSet changeSet = uow.calculateChanges();
        if (changeSet.isEmpty()) return false;

        // 子类自由实现持久化策略
        doUpdate(root, changeSet);
        return true;
    }

    protected abstract void doInsert(Root root);
    protected abstract void doUpdate(Root root, ChangeSet changeSet);
}
```

### 4.2 实现方式

| 实现方式 | 说明 | 适用场景 |
|---------|------|---------|
| **PoReconstructor** | 自动重建 PO，使用 ORM 保存 | 推荐，简洁高效 |
| **直接 JDBC** | 遍历 ChangeSet，手动生成 SQL | 高性能、精确控制 |
| **混合模式** | 主表用 ORM，子表用 JDBC | 平衡性能和开发效率 |

### 4.3 PoReconstructor 方式（推荐）

```java
@Override
protected void doUpdate(Order root, ChangeSet changeSet) {
    ReconstructedPos pos = poReconstructor.reconstruct(root, changeSet);

    // 主表
    orderPORepo.saveAll(pos.getToSave(OrderPO.class));

    // 子表
    orderItemPORepo.saveAll(pos.getToSave(OrderItemPO.class));
    orderItemPORepo.deleteAllById(pos.getToDeleteIds(OrderItemPO.class));
}
```

### 4.4 直接 JDBC 方式

```java
@Override
protected void doUpdate(Order root, ChangeSet changeSet) {
    for (Change change : changeSet.getLeafChanges()) {
        String path = change.path();

        if ("status".equals(path)) {
            jdbc.update("UPDATE t_order SET status = ? WHERE id = ?",
                ((FieldChange)change).newValue(), root.getId());
        }
        else if (path.startsWith("items[")) {
            handleItemChange(root, path, change);
        }
    }
}
```

---

## 五、关键决策点

### Q1：集合项如何识别？

使用 **`ObjectNode.identifier()` 业务标识符** 进行集合项匹配：

```java
// 配置标识符提取器
Map<Class<?>, Function<Object, Object>> extractors = Map.of(
    OrderItem.class, item -> ((OrderItem) item).getId()
);
```

### Q2：嵌套值对象如何处理？

change-tracking 库自动递归追踪嵌套对象：

```java
// 修改 item.price.amount 会生成：
FieldChange(path="items[0].price.amount", oldValue=100, newValue=150)
```

### Q3：ItemAddedChange/ItemRemovedChange 如何使用？

`addedItem` 和 `removedItem` 返回的是 **`ObjectNode`（快照节点）**，而不是领域对象：

```java
// 正确用法
for (ItemAddedChange change : additions) {
    ObjectNode itemNode = (ObjectNode) change.addedItem();
    Object identifier = itemNode.identifier();  // 提取业务 ID

    // 从聚合根中找到实际对象
    OrderItem item = root.getItems().stream()
        .filter(i -> i.getId().equals(identifier))
        .findFirst().orElseThrow();

    insertOrderItem(item, root.getId());
}
```

---

## 六、测试覆盖

| 测试类型 | 测试数量 | 状态 |
|---------|---------|------|
| 单元测试（reconstructor） | 22 | ✅ |
| 集成测试（FullIntegrationTest） | 57 | ✅ |
| **合计** | **79+** | ✅ |

---

## 七、2025-12-28 修改记录

### 7.1 修改内容

1. **`ConverterRegistry.getAllConverters()`** - 新增方法
   - 扫描所有聚合根（`CompositePoConverter`）和简单领域类（`PoConverter`）
   - 递归建立字段名到转换器的映射，支持多层嵌套

2. **`ChangeDispatcher`** - 重写变更分发逻辑
   - ~~使用 `getLeafChanges()` + 路径解析~~
   - ~~`extractDeepestCollectionField()` 从完整路径中提取最深层的已注册集合字段~~
   - **2025-12-29 更新**：使用 `change.collectionFieldName()` 替代手动路径解析，代码从 80 行简化到 49 行

3. **`PoReconstructor`** - 支持嵌套子对象查找
   - 使用 `getAllConverters()` 替代 `getChildConverters()`
   - `findChildRecursively()` 递归遍历对象图查找嵌套子对象
   - **待优化**：`extractIdentifiersFromFieldChanges()` 仍使用正则解析标识符，待缺陷 4 解决后可简化

### 7.2 ~~待解决问题~~：change-tracking 库路径设计（已解决）

**问题描述**：

`ChangeSet.getAllChanges()` 和 `getLeafChanges()` 返回的 `Change.path()` 都是**完整的扁平化路径**，而不是相对路径。

**解决方案**：

change-tracking 库已修复，`ContainerChange.children()` 现在返回**相对路径**：

```java
// 修复后的行为
ContainerChange path: items[1]
  -> child path: subItems[101]

ContainerChange path: items[1].subItems[101]
  -> child path: name
```

### 7.3 待解决问题：change-tracking 库设计缺陷

#### 缺陷：缺少集合项标识符的直接访问

**问题描述**：

`Change` 没有提供直接获取所属集合项标识符的方法。消费者需要用正则从 `fullPath()` 中解析。

**当前代码**（PoReconstructor.java:145-159）：

```java
private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\w+\\[(.+?)]");

private Set<Object> extractIdentifiersFromFieldChanges(List<FieldChange> fieldChanges) {
    final Set<Object> identifiers = new HashSet<>();
    for (final FieldChange fc : fieldChanges) {
        final Matcher matcher = IDENTIFIER_PATTERN.matcher(fc.fullPath());
        if (matcher.find()) {
            final String idStr = matcher.group(1);
            try {
                identifiers.add(Long.parseLong(idStr));
            } catch (NumberFormatException e) {
                identifiers.add(idStr);
            }
        }
    }
    return identifiers;
}
```

**期望行为**：

```java
for (final FieldChange fc : fieldChanges) {
    Object id = fc.collectionItemIdentifier();
    if (id != null) {
        identifiers.add(id);
    }
}
```

**验收标准 (Acceptance Criteria)**：

| AC | 场景 | 输入 (fullPath) | 期望输出 |
|----|------|----------------|---------|
| AC1 | 一级集合项字段 | `items[100].name` | `100L` (Long) |
| AC2 | 嵌套集合项字段 | `items[100].subItems[1001].value` | `1001L` (Long) |
| AC3 | 字符串标识符 | `specs[color].value` | `"color"` (String) |
| AC4 | 主表字段 | `status` | `null` |
| AC5 | 集合本身的变更 | `items` (ItemAddedChange) | `null`（应从 `addedItem().identifier()` 获取） |
| AC6 | 类型保持 | `items[100].name` | 返回类型与注册时 `withIdentifier()` 的提取器返回类型一致 |

**补充说明**：

- `collectionItemIdentifier()` 返回的是**最深层集合项**的标识符（与 `collectionFieldName()` 对应）
- 对于 `ItemAddedChange` / `ItemRemovedChange`，标识符应从 `addedItem().identifier()` / `removedItem().identifier()` 获取，而非此方法

---

## 八、多租户隔离（tenant_id）

> 对应 userSea Architecture ADR-001/007：共享库表 + `tenant_id` 逻辑隔离，默认 fail-closed。

### 8.1 数据分类（Global vs Tenant-scoped vs System-level）

| 分类 | `tenant_id` | 说明 | 示例 |
|------|-------------|------|------|
| **Global** | 无 | 跨租户共享数据 | User, Credential, Tenant |
| **Tenant-scoped** | 必须 | 租户内隔离数据 | TenantMembership, RoleAssignment, Department |
| **System-level** | 无 | 系统预定义且不可被租户修改 | Permission, 预置 Role |

### 8.2 PO 基类选择

- **Global / System-level**：继承 `BasePO`
- **Tenant-scoped**：继承 `TenantScopedBasePO`

### 8.3 TenantContext 传播

Repository 层从 `TenantContext`（ThreadLocal）读取 `tenantID`：

```java
TenantContext.setTenantID(tenantID);
try {
    // repository calls
} finally {
    TenantContext.clear();
}
```

### 8.4 Repository 自动注入规则（默认路径）

脚手架对 Spring Data JPA 仓储启用自定义 base class：

- 配置入口：`ProjectApplication` 的 `@EnableJpaRepositories(repositoryBaseClass=TenantAwareJpaRepositoryImpl.class)`
- 生效范围：继承 `TenantScopedBasePO` 的实体（tenant-scoped）

行为（基础 CRUD）：

- **读**：`findAll`/`findById` 自动追加 `tenant_id = 当前 tenant`；tenant 缺失时返回空结果（fail-closed）
- **写**：`save`/`saveAll` 自动注入 `tenantID`（实体未设置时），并默认禁止跨租户写

> 注意：当前自动注入覆盖 **基础 CRUD**；自定义 `@Query` / 派生查询方法需要在 code review 中确认 tenant 条件是否被正确限制。

### 8.5 `@CrossTenant` escape hatch（受控放行）

跨租户查询/写必须显式标注 `@CrossTenant`（或等价机制），仅在标注的方法作用域内临时开启：

```java
@CrossTenant
List<RoleAssignmentPO> listAllTenants() { return repo.findAll(); }

@CrossTenant
void createForTenant(String tenantID) {
    RoleAssignmentPO po = new RoleAssignmentPO();
    po.setTenantID(tenantID); // 跨租户写：必须显式指定 tenantID
    repo.save(po);
}
```

强约束：

- `@CrossTenant` 仅用于平台级后台/运维/迁移等场景
- 每次新增 `@CrossTenant` 必须 code review 强审

---

## 九、参考资料

- [Martin Fowler - Unit of Work Pattern](https://martinfowler.com/eaaCatalog/unitOfWork.html)
- [Vaughn Vernon - Implementing Domain-Driven Design](https://www.amazon.com/Implementing-Domain-Driven-Design-Vaughn-Vernon/dp/0321834577)
