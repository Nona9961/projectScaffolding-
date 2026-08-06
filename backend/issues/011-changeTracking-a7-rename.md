# Issue: changeTracking A7 改名全链路（UnitOfWork → ChangeTracker）

## 背景

changeTracking 任务 `ct-review-fix`（WU-A7）执行 D16 决策：`UnitOfWork` 继承经典 UOW 命名却改语义（new/removed 是排除标记非生命周期），误导。本质是**变更检测器**（注册对象 → 检测 UPDATE 变更），故彻底改名。

## API 变化（breaking，消费方需同步）

| 现状 | 目标 |
|---|---|
| `UnitOfWork`（`domain.model.unitofwork` 包） | `ChangeTracker`（`domain.model.tracking` 包） |
| `registerClean(entity)` | `track(entity)`（纳入追踪/建立基线） |
| `registerNew(entity)` | `excludeNew(entity)` |
| `registerRemoved(entity)` | `excludeRemoved(entity)` |
| `calculateChanges()` | 保留不变 |
| `UnitOfWorkFactory`（api 模块） | `ChangeTrackerFactory` |
| `UnitOfWorkProvider`（消费方） | `ChangeTrackerProvider` |
| `UnitOfWorkAutoConfiguration`（消费方） | `ChangeTrackerAutoConfiguration` |

## 消费方修改清单（projectScaffolding-）

1. `UnitOfWorkProvider` → `ChangeTrackerProvider`（类 + import + 方法返回类型）
2. `UnitOfWorkAutoConfiguration` → `ChangeTrackerAutoConfiguration`（Spring 配置类）
3. `DifferRepository`：`registerClean` → `track`；`registerNew` → `excludeNew`；`registerRemoved` → `excludeRemoved`；`unitOfWork` 变量 → `changeTracker`
4. 测试文件（4 个）同步适配
5. 注意：`ChangeTracker` 在 `domain.model.tracking` 包，import 路径变化

## 状态

- [x] 库侧已修复（changeTracking WU-A7，commit 33f7874，196 测试全绿）
- [ ] 消费方同步（A8 收尾执行）
- [ ] 消费方 `mvn compile` + `mvn test` 全绿
