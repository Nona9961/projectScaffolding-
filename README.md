# Project Scaffolding

企业级 Java 后端脚手架，基于 DDD（领域驱动设计）架构，专注于解决 **DDD 领域模型与 RDB 关系型数据库之间的阻抗失衡问题**。

## 技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 21 | 使用虚拟线程 |
| Spring Boot | 3.5.8 | 应用框架 |
| Spring Data JPA | 3.5.8 | ORM 框架 |
| Spring Security | 3.5.8 | 安全框架 |
| Log4j2 | 2.24.3 | 异步日志（Disruptor） |
| MySQL | 9.x | 生产数据库 |
| H2 | 2.x | 开发/测试数据库 |
| Maven | - | 构建工具 |

## 模块结构

```
projectScaffolding/
├── common/     # 公共模块：工具类、事件系统、ID生成、异常处理
├── api/        # 接口模块：DTO定义、公共API接口
└── server/     # 主应用：DDD领域模型、持久化层、控制器
```

### 模块职责

| 模块 | 职责 | 依赖 |
|------|------|------|
| **common** | 日志、工具类、事件驱动、分布式ID生成、基础仓储接口 | 无业务依赖，可独立部署 |
| **api** | 公共接口定义、DTO 模型（Record 类型）、参数验证 | common |
| **server** | 领域模型、应用服务、基础设施实现、REST 控制器 | api, common |

## 核心特性

### 1. DDD 分层架构
```
server/src/main/java/com/nona/
├── domain/           # 领域层：聚合根、值对象、领域服务、仓储接口
│   └── {aggregate}/
│       ├── entity/   # 聚合根和实体
│       ├── factory/  # 工厂
│       ├── ports/    # 防腐层接口
│       └── repo/     # 仓储接口
├── application/      # 应用层：应用服务、全局异常处理
├── controller/       # 接口层：REST 控制器
└── inf/              # 基础设施层
    ├── context/      # 请求上下文（聚合根快照）
    └── persistence/  # 持久化实现
```

### 2. 分布式 ID 生成
基于 Snowflake 算法的 `Sequence` 类，支持：
- 1024 个节点（5位数据中心ID + 5位工作机器ID）
- 每毫秒 4096 个 ID
- 时间回拨处理
- 基于 MAC/PID 自动分配工作 ID

### 3. 事件驱动架构
```java
Event → Dispatcher → EventHandler (同步/异步)
```
使用 Java 21 虚拟线程执行异步处理。

### 4. 高性能时钟
`SystemClock` 枚举单例，使用 ScheduledExecutorService 缓存时间戳，解决高并发场景下 `System.currentTimeMillis()` 性能问题。

### 5. 多租户支持
`BasePO` 包含 `tenantID` 字段，支持数据隔离。

## 快速启动

### 开发环境
```bash
# 克隆项目
git clone <repository-url>
cd projectScaffolding-

# 编译
mvn clean install

# 启动（默认使用 H2 内存数据库）
mvn spring-boot:run -pl server
```

### 配置文件
- `application.yml` - 主配置
- `application-dev.yml` - 开发环境（默认激活）
- `application-prod.yml` - 生产环境
- `application-test.yml` - 测试环境

### 端口
- 开发环境：19890

## 项目状态

### 已完成
- [x] 基础架构和多模块结构
- [x] DDD 领域模型框架
- [x] 事件驱动系统
- [x] 分布式 ID 生成
- [x] 全局异常处理
- [x] 异步日志系统
- [x] 高性能时钟方案
- [x] 请求级别的聚合根快照（ThreadContext）

### 进行中
- [ ] DDD-RDB 阻抗失衡解决方案（详见 [PLAN.md](./PLAN.md)）
- [ ] 领域对象变更追踪与差异持久化

### 待实现
- [ ] Controller 层实现
- [ ] JPA Repository 具体实现
- [ ] Redis 缓存集成

## 核心问题：DDD-RDB 阻抗失衡

本项目致力于解决 DDD 与关系型数据库之间的阻抗失衡问题：

| 问题 | 描述 |
|------|------|
| **对象-关系映射** | 领域对象是面向对象的，RDB 是关系型的 |
| **聚合边界** | 聚合根可能对应多张表，难以保证事务一致性 |
| **值对象持久化** | 值对象无标识，但数据库需要主键 |
| **变更追踪** | 需要知道领域对象的哪些部分发生了变化 |
| **懒加载与贪婪加载** | 聚合内对象的加载策略难以平衡 |

详细解决方案请参考：[PLAN.md](./PLAN.md)

## 许可证

MIT License
