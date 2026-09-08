# Schema 迁移规范（Flyway）

> 面向派生项目的 DDL 版本化管理约定：脚本怎么写、什么时候能改、破坏性变更怎么拆、
> 已有库怎么接入、多租户/多库形态怎么演进。

## 机制

- **Flyway 负责 schema 演进**（启动时自动执行 `db/migration` 下未应用的迁移）；
  **Hibernate `ddl-auto: validate` 负责一致性校验**——实体映射与迁移后的表不一致时启动失败（fail-fast）。
- 已执行脚本由 `flyway_schema_history` 版本表记录 checksum；**篡改已执行脚本 = 启动失败**。

## 脚本约定

1. 命名：`V{版本}__{描述}.sql`（如 `V2__create_order.sql`），版本号全局递增，**由主线统一分配**，分支不预占号，避免合并冲突
2. **只增不改**：已提交/已执行的脚本永不修改；任何变更 = 新版本脚本（改旧脚本会破坏 checksum 校验）
3. 一逻辑变更一脚本：一个版本号是一个原子变更单元，评审按版本审查
4. `clean` 永久禁用（`spring.flyway.clean-disabled: true`，模板默认已配）

## 破坏性变更纪律（expand-contract）

rename / drop / 类型变更**不得一步到位**，拆为两个迁移脚本、跨部署窗口执行：

1. **扩展**：加新列/新表（可空或带默认值），应用双写或回填
2. **验证**：新旧路径产出一致
3. **收缩**：所有读取切到新结构后，再删旧列/旧表

## 已有库接入（baseline）

已有表且无版本表的库（如从旧项目接管的库）：

1. 开启 `spring.flyway.baseline-on-migrate`（或首次迁移前手动 `baseline`）
2. V1 = 现有结构 dump（`mysqldump` 或 Hibernate schema-generation 生成后人工修正索引/约束）
3. 之后所有变更按规范走版本迁移

## 环境策略

| 环境 | 数据库 | 行为 |
|------|--------|------|
| dev / test | H2 内存 | 每次启动空库全量重放全部迁移 = 天然的可重放性验证（内存库销毁即重放，无需 clean；且 `clean-disabled` 模板默认常开） |
| prod | 派生项目自选（MySQL / PostgreSQL） | 增量迁移；`clean-disabled` 常开 |

- 方言模块：MySQL 用 `flyway-mysql`，PostgreSQL 换 `flyway-database-postgresql`（模板默认携带前者）
- 换库 = 换方言模块 + 迁移脚本按目标库方言编写（脚本是库绑定资产，随项目走）

## 多租户 / 多库预案

- **同构多库**（分库 / 每租户一库）：Flyway 官方循环迁移模式——对每个目标库调用 migrate（切换 `flyway.schemas` / url），所有库执行同一批脚本
- **异构并跑**（如 MySQL 主库 + PostgreSQL 镜像读库）：双 Flyway 实例各管各的迁移目录与版本表，两库结构不同源，各自版本化

## 测试

- 测试 profile 由 Hibernate `create-drop` 建表（与迁移机制解耦），测试关注业务行为，不承担迁移验证
- 需要 CI 验证迁移可重放性的项目：测试环境启用 Flyway（空库全量重放），并按需在 `src/test/resources/db/migration` 放测试专用迁移