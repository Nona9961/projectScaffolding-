# Issue: changeTracking A5 泛型收紧（SnapshotStrategy 参数化）

## 背景

changeTracking 任务 `ct-review-fix`（WU-A5）修复 A5 设计缺陷：`TrackingCapability` 泛型设计削弱类型安全——`SnapshotStrategy`（SPI）无类型参数 → `getSnapshotStrategy()` 返回 raw 类型 → `UnitOfWork` 两处 unchecked 强转 + `@SuppressWarnings`（review 定性「既 raw 又强转又 suppress」最差组合）。

## API 变化（源码级 breaking，仅影响 SPI 实现者）

1. **`SnapshotStrategy` 加类型参数**：`SnapshotStrategy<S extends Snapshot<?>>`，`createSnapshot` 返回 `S`——实现者声明快照类型，编译期确定返回类型
2. **`TrackingCapability.getSnapshotStrategy()`** 返回 `SnapshotStrategy<S>`（raw 消除）

二进制兼容（擦除后签名不变）；`TrackingCapability` 返回值泛型化对 raw 赋值调用方仍源码兼容。

## 消费方影响

**零改动**：grep 实证消费方只使用 `TrackingCapabilityProvider`/`UnitOfWork`/`DefaultTrackingCapabilityProvider`，零实现、零调用 `SnapshotStrategy`/`getSnapshotStrategy`。无需代码修改。

## 状态

- [x] 库侧已修复（changeTracking WU-A5，commit 2096b8f）
- [ ] 消费方验证（A8 收尾统一执行）
