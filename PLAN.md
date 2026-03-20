# Backend Plan

## Multi-tenancy base（ADR-001 / ADR-007）

### 1) 数据分类（Global vs Tenant-scoped vs System-level）

| 类型 | tenant_id | 说明 | 示例 |
|------|-----------|------|------|
| Global | 无 | 跨租户共享的身份/元数据 | User, Credential, Tenant |
| Tenant-scoped | 必须 | 租户内隔离的数据 | Order, Department, RoleAssignment |
| System-level | 无 | 系统级运行数据（平台/运维） | Job, Audit, Migration |

### 2) PO 基类选择

- `BasePO`：Global / System-level（不含 `tenant_id`）
- `TenantScopedBasePO`：Tenant-scoped（含 `tenant_id` + `@TenantId`）

### 3) 运行时 tenant 传播

- tenantID 存放在 `ThreadContext.tenantID`（RequestScope）
- 推荐在 Web 入口（Filter/Interceptor）解析 tenantID（Header/Token）并写入 `ThreadContext`

### 4) Hibernate discriminator multi-tenancy（读隔离）

对继承 `TenantScopedBasePO` 的实体：
- Hibernate 通过内置 `_tenantId` filter 自动追加 `tenant_id = :tenantId`
- tenantID 由 `ThreadContextTenantIdentifierResolver` 提供：
  - tenant 缺失：返回 `__MISSING_TENANT__` 实现 fail-closed（tenant-scoped 查询默认返回空）
  - cross-tenant：返回 root tenant（见 `@CrossTenant`）

配置入口：
- `HibernateMultiTenancyConfig`：注册 `MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER`

### 5) 写入规则（fail-closed + 禁止跨租户写）

对 tenant-scoped 写入：
- tenant 缺失：直接失败
- entity 未设置 `tenant_id`：自动注入当前 tenantID
- entity 显式设置了与当前 tenant 不一致的 `tenant_id`：默认拒绝（防止跨租户写）

实现入口：
- `TenantRepositoryAspect`

### 6) `@CrossTenant` escape hatch（受控放行）

跨租户查询/写必须显式标注 `@CrossTenant`（或等价机制），仅在标注的方法作用域内临时开启：

```java
@CrossTenant
public List<OrderPO> listAllTenantsOrders() {
    return orderJpaRepository.findAll();
}
```

实现入口：
- `@CrossTenant`
- `CrossTenantAspect`：以 `HIGHEST_PRECEDENCE` 运行，确保在事务开始前生效

约束：
- cross-tenant 写入必须显式指定 `tenant_id`
- `@CrossTenant` 仅用于平台级后台/运维/迁移等场景，必须 code review 强审

### 7) 测试

- `TenantRepositoryAspectTest`：覆盖 tenant 过滤、tenant 缺失 fail-closed、跨租户写拒绝、`@CrossTenant` 放行与作用域收敛等关键场景

