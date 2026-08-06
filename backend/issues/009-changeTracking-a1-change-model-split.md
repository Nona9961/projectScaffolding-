# Issue: changeTracking A1 变更模型拆分（FieldChange → ValueChange + ObjectFieldChange）

## 背景

changeTracking 任务 `ct-review-fix`（WU-A1）修复 A1 缺陷：类型变化（object→null、collection→object 等）时 `FieldChange.oldValue()/newValue()` 泄漏 ValueNode 内部实例，下游强转业务类型（`(Money) fc.newValue()`）会抛 ClassCastException。

## API 变化（breaking）

1. **`FieldChange` → `ValueChange`**（改名）：仅承载基本值变更（PrimitiveNode↔PrimitiveNode、PrimitiveNode↔NullNode），`oldValue()/newValue()` 保证是业务值
2. **新增 `ObjectFieldChange`**（sealed `Change` 新实现）：承载对象/集合字段整体变更（object→null、null→object、object→primitive、collection↔*），携带 `ValueNode oldNode/newNode`（无业务对象——快照不持原始引用，见 changeTracking 设计决策 D5）
3. **`ItemAddedChange.addedItem()` / `ItemRemovedChange.removedItem()` 载荷类型 `Object` → `ValueNode`**：声明与事实对齐；消费方现有强转 `((ObjectNode) iac.addedItem())` 编译不受影响

## 消费方需要做的修改

| 文件 | 修改 |
|---|---|
| `ChangeDispatcher.java` | `instanceof FieldChange` → `instanceof ValueChange`；新增 `ObjectFieldChange` 处理分支（如 object→null → UPDATE null；sealed switch 需加 case，default 分支会吞掉新类型——按业务语义显式处理） |
| `PoReconstructor.java` | 同上：`FieldChange` 引用改 `ValueChange` |
| `OrderRepository`（测试）/ `FullIntegrationTest` / `PoReconstructorTest` | `instanceof FieldChange` → `instanceof ValueChange`；`ObjectFieldChange` 断言用 `newNode()`（`NullNode`=清空、`ObjectNode`=赋值） |

## 语义对照（消费方判断新状态用）

- `ObjectFieldChange.newNode()` 为 `NullNode` → 字段被清空（赋 null）
- `ObjectFieldChange.newNode()` 为 `ObjectNode`/`CollectionNode` → 字段被整体赋值（用 ValueNode 树重建）
- 原 `FieldChange` 场景（基本值变化）行为不变

## 状态

- [ ] 库侧修复中（changeTracking WU-A1）
- [ ] 消费方同步（A8 收尾统一执行：改代码 + `mvn compile` + `mvn test` 全绿）
