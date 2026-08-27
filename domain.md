# 领域文档

> 本文件回答三个问题：这个项目属于什么领域、做什么事情、代码如何映射到领域。
> 它是当前状态的快照，随代码演进更新；设计理由与决策记录不在此处。

## 领域定位

projectScaffolding 属于**后端应用脚手架**领域：它不是一个业务系统，而是一个可复用的
新项目起点——把三类高频且易错的能力固化下来：**DDD 分层骨架、属性级变更追踪持久化、
多租户隔离**。新项目基于它填充业务代码即可上线，无需从零搭建。

## 做什么

1. **提供 DDD 分层骨架**：聚合根 / 值对象 / 工厂 / 仓储 / ACL 的分层约定固化，
   业务代码按约定落入对应位置。
2. **变更追踪持久化**：集成 changeTracking 框架，仓储层自动完成属性级变更计算与
   PO 重建，业务侧只写业务差异逻辑。
3. **开箱即用的多租户隔离**：请求上下文携带租户 / 角色 / 身份三元组，租户缺失时
   默认不放行数据（fail-closed）；写门禁规则上移 common（纯函数两条件判定，覆盖所有
   带实体的写操作，与操作方法名无关），作用域退出自动清理数据层缓存（I2）；跨租户
   访问需显式受控放行；异步线程池中上下文自动传播。
4. **模板可追溯**：脚手架生成的文件带 `@ScaffoldGenerated` 标识，模板产物与手写
   代码一眼可辨。

## 领域概念与代码映射

| 领域概念 | 是什么 | 代码位置 |
|---------|--------|---------|
| DDD 分层元素 | 聚合根 / 值对象 / 工厂 / 仓储 / ACL 的约定骨架 | `server/domain/<aggregate>` → `entity`、`factory`、`repo`、`ports` |
| 请求上下文三元组 | 租户 / 角色 / 身份，随请求贯穿业务与异步链路 | `server/inf/context` → `ThreadContext`、`TenantContextAccessor` |
| 多租户隔离 | 按租户切分数据，租户缺失不放行；跨租户访问显式放行；写门禁规则存储无关 | 规则：`common/tenant` → `TenantWriteGate`、`TenantScopeExitHandler`；JPA 实现：`server/inf/persistence/tenant` → Hibernate 配置、`TenantRepositoryAspect`、`JpaTenantScopeExitHandler`、resolver |
| 变更追踪集成 | 仓储自动计算属性级变更并重建 PO | `server/inf/persistence/tracking`（自动配置、`ChangeTrackerProvider`）、`repository` → `DifferRepository`、`reconstructor` |
| 模板生成标识 | 脚手架生成文件的标记 | `common/annotation` → `@ScaffoldGenerated` |
| 公共基础设施 | 统一响应、业务异常、断言、事件总线、ID 生成 | `common` 模块 → `api`、`exceptions`、`util`、`events`、`persistence` |
| 对外契约 | 供外部调用方消费的 API 与 DTO | `api` 模块 → `PublicApi`、`dto` |

## 边界

- 不是业务系统：不包含任何业务领域（`domain/rootPackage` 仅为骨架示例）。
- 不强制架构风格：不做 CQRS / 事件驱动 / 读写分离的强制约束，业务项目按需引入。
- 不内置认证授权：只提供身份上下文的承载与传播，认证方案由具体项目决定。
