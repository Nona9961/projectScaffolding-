# Issue: changeTracking A4 快照模型不可变改造（fields()/items() 移除）

## 背景

changeTracking 任务 `ct-review-fix`（WU-A4）修复 A4 缺陷：`ObjectNode`/`CollectionNode` 由 record 改为 final class + 只读方法 API，实现真不可变。

## API 变化

| 旧 API（已移除） | 新 API |
|---|---|
| `ObjectNode.fields()` → `Map<String, ValueNode>` | `ObjectNode.field(String)` / `ObjectNode.forEachField(BiConsumer)` |
| `CollectionNode.items()` → `List<ValueNode>` | `CollectionNode.size()` / `CollectionNode.item(int)` / `CollectionNode.forEachItem(Consumer)` |
| `ObjectNode.identifier()` | 不变 |

`ValueNode` sealed 层级、`PrimitiveNode`/`NullNode` 不变。

## 消费方影响

**零影响（非 breaking）**：全项目 grep 验证消费方无 `fields()`/`items()` 调用（`PoReconstructor` 仅用 `identifier()`）。无需代码修改。

## 状态

- [x] 库侧已修复（changeTracking WU-A4，commit f20a6eb）
- [ ] 消费方验证（A8 收尾统一执行：`mvn compile` + `mvn test` 全绿）
