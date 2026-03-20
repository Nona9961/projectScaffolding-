# Project Scaffolding

企业级 Java 后端脚手架，基于 DDD 架构，专注于解决 **DDD 领域模型与关系型数据库之间的阻抗失衡问题**。

## 快速开始

```bash
# 编译并安装
mvn clean install

# 启动（默认 H2 内存数据库）
mvn spring-boot:run -pl server

# 访问
# http://localhost:19890
# H2 控制台：http://localhost:19890/h2
```

## 核心流程

```
┌─────────────────────────────────────────────────────────────┐
│  加载聚合根 → 注册到 UOW → 业务修改 → 计算变更 → 重建 PO → 持久化  │
└─────────────────────────────────────────────────────────────┘
```

## 三步快速上手

### 1. 定义领域模型

```java
// 聚合根
class Order {
    Long id;
    OrderStatus status;
    List<OrderItem> items;  // 子实体

    void addItem(OrderItem item) { items.add(item); }
    void confirm() { status = CONFIRMED; }
}

// 值对象（不会被递归追踪）
record Money(BigDecimal amount) {}
```

### 2. 配置 UOW（核心！）

```java
// 在 Repository 构造时配置
UnitOfWorkProvider uowProvider = UnitOfWorkProvider.builder()
    .withIdentifier(Order.class, Order::getId)
    .withIdentifier(OrderItem.class, OrderItem::getId)
    .withValueType(Money.class)      // 值类型不展开追踪
    .build();

// UOW 自动追踪：
// 1. getByID() 时注册快照
// 2. 修改字段时记录变更
// 3. save() 时生成 ChangeSet
```

### 3. 实现 Repository

```java
// 继承 DifferRepository
class OrderRepositoryImpl extends DifferRepository<Order, OrderPO, Void>
        implements OrderRepository {

    @Override
    protected void doInsert(Order root) {
        orderPO.save(toPO(root));           // 新增主表
        itemPO.saveAll(toPOs(root.items));  // 新增子表
    }

    @Override
    protected void doUpdate(Order root, ChangeSet changeSet) {
        ReconstructedPos pos = reconstructor.reconstruct(root, changeSet);

        orderPO.saveAll(pos.getToSave(OrderPO.class));           // 主表
        itemPO.saveAll(pos.getToSave(OrderItemPO.class));        // 子表新增+更新
        itemPO.deleteAllById(pos.getToDeleteIds(OrderItemPO.class)); // 子表删除
    }
}
```

## 配置说明

### 配置文件（application.yml）

```yaml
change-tracking:
  default-identifier: id              # 默认标识字段
  value-type-packages:                # 值类型包
    - com.nona.domain.order.vo
  value-types:                        # 值类型类
    - com.nona.domain.common.Money
```

### 标识符配置优先级

```
方法配置 > 字段覆盖 > 默认字段
```

## 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 21 | 虚拟线程 |
| Spring Boot | 4.0.3 | 应用框架 |
| Spring Data JPA | 4.0.3 | ORM |
| Log4j2 | 2.24.3 | 异步日志 |

## 模块结构

```
projectScaffolding/
├── common/     # 公共模块（工具、事件、ID生成）
├── api/        # 接口模块（DTO、API定义）
└── server/     # 主应用（DDD领域模型、持久化）
```

## 多租户（tenant_id）约定

脚手架采用 **共享库表 + `tenant_id` 逻辑隔离**（ADR-001）。按数据归属分三类：

| 分类 | `tenant_id` | 说明 | 示例 |
|------|-------------|------|------|
| **Global** | 无 | 跨租户共享的身份/元数据 | User, Credential, Tenant |
| **Tenant-scoped** | 必须 | 租户内隔离的数据 | TenantMembership, RoleAssignment, Department |
| **System-level** | 无 | 系统预定义且不可被租户修改 | Permission, 预置 Role(OWNER/ADMIN/MEMBER) |

### PO 基类选择（BasePO vs TenantScopedBasePO）

- **Global / System-level**：继承 `BasePO`（不含 `tenant_id`）
- **Tenant-scoped**：继承 `TenantScopedBasePO`（含 `tenant_id`）

### TenantContext 传播

Repository 自动从 `TenantContext`（ThreadLocal）读取 `tenantID`。通常由认证中间件从 JWT 的 `tenantId` claim 提取后设置：

```java
TenantContext.setTenantID(tenantID);
try {
    // do business
} finally {
    TenantContext.clear();
}
```

### Repository 自动注入规则（fail-closed）

对继承 `TenantScopedBasePO` 的实体：

- **读**：`findAll`/`findById` 自动追加 `tenant_id = 当前 tenant`；当 `tenantID` 缺失时返回空结果（fail-closed）
- **写**：`save`/`saveAll` 自动注入 `tenantID`（当实体 `tenantID` 为空时），并默认禁止跨租户写

实现位置：`@EnableJpaRepositories(repositoryBaseClass=TenantAwareJpaRepositoryImpl.class)`（见 `server/src/main/java/com/nona/ProjectApplication.java`）。

> 说明：当前脚手架对 **基础 CRUD** 做了自动注入；自定义 `@Query` / 派生查询方法应在 code review 中确认 tenant 条件是否被正确限制。

### `@CrossTenant`（受控放行）

跨租户查询/写必须显式标注 `@CrossTenant`（或等价机制），默认路径仍走 tenant 注入。

```java
@CrossTenant
List<RoleAssignmentPO> listAllTenants() {
    return roleAssignmentRepo.findAll();
}

@CrossTenant
void createForTenant(String tenantID) {
    RoleAssignmentPO po = new RoleAssignmentPO();
    po.setTenantID(tenantID); // 跨租户写：必须显式指定 tenantID
    roleAssignmentRepo.save(po);
}
```

注意事项（强约束）：

- `@CrossTenant` 仅用于平台级后台/运维/迁移等场景
- 每次新增 `@CrossTenant` 必须 code review 强审，避免数据泄露

## 项目状态

**已完成**
- [x] DDD 领域模型框架
- [x] 变更追踪与差异持久化
- [x] PoReconstructor 自动重建 PO
- [x] 事件驱动架构
- [x] 分布式 ID 生成

## 许可证

MIT

