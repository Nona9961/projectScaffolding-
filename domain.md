# 领域文档

> 脚手架领域的领域概念、建模决策与边界。
> 本文只写代码无法表达的信息：机制细节与决策记录见 `.trellis/spec/projectScaffolding/backend/`
> （[索引](../.trellis/spec/projectScaffolding/backend/index.md)），类级契约见各文件 Javadoc，
> 项目定位与快速上手见 [README.md](README.md)。

## 领域定位

脚手架本身不是业务系统，而是一个**可生成的工程骨架**：为 DDD 风格的企业级后端提供开箱即用的
分层结构、变更追踪持久化与多租户隔离。下游项目基于模板生成代码，再填充自己的业务领域。

- 上游：模板生成——`@ScaffoldGenerated` 标识文件来源（模板 vs 手写），维护路径清晰。
- 下游：业务项目的领域模型与基础设施——脚手架不包含任何业务领域知识。

## 领域概念与语言

| 概念 | 领域含义 | 机制细节 |
|------|---------|---------|
| 聚合根 / 工厂 / 仓储 | DDD 战术模式：聚合根承载业务不变量，工厂负责创建，仓储负责持久化 | [directory-structure.md](../.trellis/spec/projectScaffolding/backend/directory-structure.md) |
| 请求上下文（tenantID / role / identity） | 一次请求的身份三元组：租户、角色、调用者；跨线程传播只携带这三者 | [context-propagation.md](../.trellis/spec/projectScaffolding/backend/context-propagation.md) |
| 租户隔离（fail-closed） | 租户缺失时查询放空、写入拒绝——安全优先：宁可误伤，不可越权 | [database-guidelines.md](../.trellis/spec/projectScaffolding/backend/database-guidelines.md)（ADR-001） |
| 跨租户放行（@CrossTenant） | 平台级后台 / 运维 / 迁移的显式例外通道，默认路径仍受隔离约束 | [database-guidelines.md](../.trellis/spec/projectScaffolding/backend/database-guidelines.md)（ADR-007） |
| 变更追踪（Change Tracking） | 聚合根内存修改 → 变更集 → PO 重建，持久化层只消费差异 | [database-guidelines.md](../.trellis/spec/projectScaffolding/backend/database-guidelines.md) |
| 模板标识（@ScaffoldGenerated） | 文件来源身份：模板生成 vs 手写 | [context-propagation.md](../.trellis/spec/projectScaffolding/backend/context-propagation.md) |

## 建模决策

- **请求上下文只传播身份三元组**，不传播 attributes / snapshots：可变状态跨线程不安全，
  快照必须是不可变拷贝。
- **追踪生命周期绑定请求**：ChangeTracker 与快照基线随请求创建、随请求释放——避免跨请求状态污染，
  也是多租户隔离（请求级上下文）的自然延伸。
- **fail-closed 优先于便利**：租户缺失时宁可不返回数据、拒绝写入，也不冒险放行；
  cross-tenant 写入时，未指定租户的实体注入当前租户，显式指定的保留原值（支持平台级跨租户维护）。
- **@CrossTenant 是受控例外**：仅限平台级后台 / 运维 / 迁移场景，每次新增必须 code review 强审，
  防止数据越权泄露——这是使用边界，不是性能或便利手段。

## 建模边界

- **脚手架不含业务领域知识**：`rootPackage` 等示例仅作模板演示，下游项目按约定替换。
- **不强制 CQRS / 事件驱动**：业务复杂度未达到该级别，保持实用主义 DDD。
- **不内置认证授权策略**：安全（登录、鉴权、端点放行）由下游项目自行配置，脚手架只保证
  租户维度的隔离基线。
