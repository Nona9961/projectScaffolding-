# 结构文档

> 项目的代码地图：模块划分、包结构与依赖方向（概述级）。
> 类级职责与契约见各文件 Javadoc；目录结构约定见
> [directory-structure.md](../.trellis/spec/projectScaffolding/backend/directory-structure.md)
> （本文描述本项目现状，不照搬模板）。

## 模块划分

| 模块 | 内容 | 项目内依赖 |
|------|------|-----------|
| `common` | 跨模块公共能力：异常、断言、统一响应、事件总线、ID 生成、`@ScaffoldGenerated` | 无 |
| `api` | 对外 API 契约与 DTO（record），供外部调用方消费 | `common` |
| `server` | Spring Boot 应用：DDD 领域层 + 基础设施（持久化 / 多租户 / 变更追踪） | `api`、`common`、`change-tracking-api` |

> 依赖方向与 [directory-structure.md](../.trellis/spec/projectScaffolding/backend/directory-structure.md)
> 约定一致，无偏差。

## 包地图

### common

| 包 | 职责 |
|----|------|
| `com.nona.api` | 统一响应体 `HttpResponse` |
| `com.nona.annotation` | `@ScaffoldGenerated` 模板生成标识 |
| `com.nona.events` | 事件总线：`Event` / `Dispatcher` / `EventHandler` / `AbstractHandler` |
| `com.nona.exceptions` | `BusinessException`（唯一的业务异常） |
| `com.nona.persistence` | `BaseRepository`（仓储接口）、`Sequence`（Snowflake ID 实现） |
| `com.nona.util` | `BusinessAssert`（断言）、`IDUtils`（ID 生成入口）、`JacksonUtil`（JSON 工具） |

### api

| 包 | 职责 |
|----|------|
| `com.nona.api` | `PublicApi` 接口（对外契约入口） |
| `com.nona.dto` | 对外 DTO（record） |

### server

| 包 | 职责 |
|----|------|
| `com.nona` | 应用入口 `ProjectApplication`（JPA 仓库扫描配置） |
| `com.nona.application.advice` | 全局异常处理 `ExceptionAdviser` |
| `com.nona.domain.<aggregate>` | DDD 领域层：`entity`（聚合根/值对象/枚举）、`factory`、`ports`（ACL）、`repo` |
| `com.nona.inf.context` | 跨切面请求上下文：`ThreadContext`、`TenantContextAccessor`、`@CrossTenant`、异步传播装饰器 |
| `com.nona.inf.persistence.po` | JPA PO 基类：`BasePO` / `TenantScopedBasePO` |
| `com.nona.inf.persistence.converters` | DO ↔ PO 转换：`PoConverter` / `CompositePoConverter` / `ConverterRegistry` |
| `com.nona.inf.persistence.repository` | `DifferRepository`（基于变更追踪的仓储基类） |
| `com.nona.inf.persistence.dispatcher` | 变更分发：变更集分类为主表 / 子表变更 |
| `com.nona.inf.persistence.reconstructor` | PO 重建：从变更集重建待持久化 / 待删除的 PO |
| `com.nona.inf.persistence.tracking` | changeTracking 集成：自动配置、`ChangeTrackerProvider`、配置属性 |
| `com.nona.inf.persistence.tenant` | 多租户：Hibernate 配置、`TenantRepositoryAspect`、tenant resolver |

## 依赖方向

自底向上：

1. `common` —— 无项目内依赖，被 `api`、`server` 依赖
2. `api` → `common`
3. `server` → `api` + `common` + 外部 `change-tracking-api`

## 阅读起点

按调用链从入口到实现：

1. `ProjectApplication`（`server`）——应用入口，理解 JPA 扫描与自动配置范围
2. `DifferRepository`（`inf.persistence.repository`）——变更追踪与持久化的核心集成点
3. `TenantContextAccessor`（`inf.context`）——多租户与请求上下文的统一入口
4. `inf.persistence.tenant`——多租户隔离的实现（Hibernate 配置 / AOP 拦截 / resolver）
