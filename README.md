# Project Scaffolding

企业级 Java 后端脚手架：以 **DDD 分层骨架 + 属性级变更追踪 + 开箱即用的多租户隔离** 为核心，
作为新项目的起点模板。基于 [changeTracking](https://github.com/Nona9961/changeTracking) 框架与 Spring Boot 4.1。

## 动机

- **DDD 落地成本高**：聚合根在内存完成业务操作后，持久化层需要精确知道「哪些属性变了」才能生成
  准确的 UPDATE——DDD 领域模型与关系型数据库之间存在阻抗失衡。脚手架将领域分层约定、变更追踪
  与 Repository 的集成一次性固化，新项目直接填充业务即可，无需从零搭建。
- **多租户是共性需求且容易出错**：租户隔离一旦缺失或失效（例如异步线程池丢失请求上下文）会造成
  数据越权。脚手架内置 fail-closed 的隔离机制与跨线程上下文传播设施，避免每个项目各自实现、各自踩坑。
- **模板代码需要可追溯**：脚手架生成的文件与项目手写文件需要一眼可辨，Bug 修复时才能知道往
  脚手架提 issue 还是在项目内修改。

## 亮点

- **DDD 分层骨架开箱即用**：聚合根 / 值对象 / 工厂 / 仓储 / ACL 分层约定固化，读写分离的实用主义调整
- **变更追踪持久化**：集成 changeTracking，仓储层自动完成属性级变更计算与 PO 重建，业务侧只写差异逻辑
- **多租户隔离默认安全**：fail-closed 设计（租户缺失不放行数据），跨租户访问需显式受控放行
- **异步上下文不丢失**：线程池中租户 / 角色 / 身份上下文自动传播，异步场景租户隔离依旧有效
- **模板标识清晰**：所有脚手架生成文件带 `@ScaffoldGenerated` 标识，模板与手写代码一目了然
- **安全默认**：仅暴露 `health` / `info` 两个 Actuator 端点，其余端点由具体项目自行决定
- **现代技术栈**：Java 25 虚拟线程、Spring Boot 4.1

## 多租户

tenant-scoped 数据基于 Hibernate `@TenantId` 自动读写隔离；写入门禁是 common 层的纯函数判定
（`TenantWriteGate`：提权状态 × 实体归属两条件，覆盖所有带实体的写操作，与操作方法名无关；
ID/无参删除由 filter 兜底）。提权/读放行作用域退出时自动 `flush()+clear()`（`TenantScopeExitHandler`
SPI），保证数据层缓存与当前视角一致。跨租户**写**必须 `TenantPrivilege` 提权（提权下实体须显式
归属）；跨租户**读**可用 `@CrossTenant`（只关读过滤）或提权。fail-closed：上下文租户缺失时不放行
任何 tenant-scoped 数据——查询返回空集、写入直接拒绝。

详细用法见[多租户使用手册](docs/multitenancy-guide.md)。

## 前置依赖：changeTracking SDK

本仓库的变更追踪能力来自独立的 [changeTracking](https://github.com/Nona9961/changeTracking) 仓库
（`change-tracking-api` / `change-tracking-core`），该 SDK **未发布到 Maven 中央仓库**，直接克隆本
仓库无法构建——必须先克隆 changeTracking 并安装到本地 Maven 仓库：

```bash
# 1. 克隆并安装 changeTracking（api + core 同时装入本地 m2）
git clone https://github.com/Nona9961/changeTracking.git
cd changeTracking
mvn install            # 首次可追加 -DskipTests 加速

# 2. 克隆本仓库
git clone https://github.com/Nona9961/projectScaffolding-.git
cd projectScaffolding-

# 3. 全量验证 / 启动
mvn clean test
mvn spring-boot:run -pl server
```

## 快速开始

```bash
mvn clean install

# 启动（默认 H2 内存数据库）
mvn spring-boot:run -pl server

# 访问
# http://localhost:19890
# H2 控制台：http://localhost:19890/h2
```

### 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 25 | 虚拟线程 |
| Spring Boot | 4.1.0 | 应用框架 |
| Spring Data JPA | Spring Boot BOM 管理 | ORM |
| Log4j2 | 2.26.1 | 异步日志 |

> 关键约定（标识符配置、租户规则、变更追踪配置等）见内部规范文档，不再于此重复。

## 许可证

MIT