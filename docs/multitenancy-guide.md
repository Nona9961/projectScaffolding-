# 多租户使用手册

> 面向脚手架使用者的操作手册：怎么做、什么不能做、做错了是什么现象。
> 技术事实以仓库当前实现为准（关键类：`TenantContextAccessor` / `TenantPrivilege` /
> `ThreadContextTenantIdentifierResolver` / `TenantRepositoryAspect`）。

---

## 1. 核心模型

### 1.1 三类数据表

| 类别 | 落地方式 | 隔离行为 |
|------|----------|----------|
| **Global** | 继承 `BasePO` | 无 `tenant_id` 列，任何上下文可读写，不参与过滤 |
| **Tenant-scoped** | 继承 `TenantScopedBasePO` | 带 `@TenantId tenantID` 列，读写自动隔离 |
| **System-level** | 无独立基类，由项目自定 | 平台级数据（如平台配置、租户目录）。可建为 Global 表；
  若建为 tenant-scoped 表则仅应在提权作用域内访问（见 §4） |

选型规则：一条数据「是否天然属于某个租户」——是则 tenant-scoped，否则 Global。
拿不准时选 tenant-scoped（默认安全），事后放开比事后补救容易。

### 1.2 身份三元组 ≠ 提权状态

这是两套互不相干的机制，混为一谈是绝大多数误用的根源：

| | 身份三元组 | 提权状态 |
|---|-----------|----------|
| 内容 | `tenantID` / `role` / `identity` | 是否处于提权作用域（布尔） |
| 载体 | `ThreadContext`（request-scoped bean）+
  异步快照 `ContextSnapshot` | `TenantPrivilege` 内的 `ScopedValue<Boolean>` |
| 跟随谁 | **随人走**：请求带着它流转，
  异步经快照传播到 worker | **跟代码位置走**：仅在 `elevated { ... }`
  块内为真，出块即恢复，不随线程传播 |

身份回答「我是谁、我在哪个租户」；提权回答「这段代码此刻是否被允许跨租户」。
提权不会改变你的租户身份，也不会传播给异步任务。

---

## 2. 读隔离原理与定型语义

### 2.1 resolver 只在 session 打开时调用一次

读隔离基于 Hibernate discriminator multi-tenancy（`@TenantId`）。底层是一个命名 filter
`_tenantId`（条件 `tenant_id = :tenantId`），参数来自 resolver：

```java
// ThreadContextTenantIdentifierResolver —— session 打开的瞬间调用一次
public String resolveCurrentTenantIdentifier() {
    if (TenantPrivilege.isActive()) {
        return ROOT_TENANT_ID;          // 提权 → root 视角，不启用过滤
    }
    return tenantContextAccessor.getTenantIDOrMissing();  // 缺失 → __MISSING_TENANT__（fail-closed）
}
```

返回值**绑定该 session 终身**。由此推出两条铁律：

1. **同一事务内不可切换视角**。事务开始 → session 打开 → 租户模式定型，之后本事务内所有
   查询都用这个视角。
2. **需要另一个视角 = 新 session**。跨视角的操作必须拆到不同事务。

### 2.2 提权作用域必须罩住事务边界

由于读路径的开关就是「session 打开那一刻 resolver 读到什么」，提权作用域若晚于事务开启才进入，
session 已按原租户定型，作用域内查询**仍然被过滤**：

```java
// ❌ 错误：作用域罩不住事务边界，提权对已定型的 session 无效
@Transactional
public List<Order> listAllOrders() {
    return TenantPrivilege.elevated(() -> orderRepository.findAll());   // 依旧只看到本租户
}

// ✅ 正确一：组合 API，先提权再开事务
public List<Order> listAllOrders() throws Exception {
    return TenantPrivilege.elevatedInTransaction(transactionTemplate,
            () -> orderRepository.findAll());
}

// ✅ 正确二：手工保证顺序——elevated 包在 @Transactional 方法外面
public List<Order> listAllOrders() throws Exception {
    return TenantPrivilege.elevated(() -> self.transactionalListAll());
}
```

无事务的短命查询（每次 repository 调用各开一个 session）不受此限：只要调用发生在作用域内，
新 session 打开时即可读到提权状态。证例见
`server/src/test/java/com/nona/inf/persistence/tenant/TenantRepositoryAspectTest.java`
的 `elevatedScopeShouldExposeAllTenantsInsideAndRestoreIsolationAfterExit`。

---

## 3. 写入门禁

`TenantRepositoryAspect` 拦截所有 Spring Data Repository 的 `save` / `saveAll`，
对 tenant-scoped 实体执行门禁。

### 3.1 非提权状态：三条规则

```java
// 规则一：实体无租户 → 注入当前租户
po.setTenantID(null); repo.save(po);          // ✅ 落库为当前租户

// 规则二：显式租户一致 → 放行
po.setTenantID("t1"); /* 当前 t1 */ repo.save(po);   // ✅

// 规则三：显式租户不一致 → 拒绝
po.setTenantID("t2"); /* 当前 t1 */ repo.save(po);
// ❌ BusinessException: cross-tenant write is forbidden. currentTenant=t1, entityTenant=t2
```

前置约束：**当前上下文必须有租户**。`threadContext.setTenantID(null)` 或空白时保存直接抛
`BusinessException: tenantID is required for tenant-scoped write operation`。

### 3.2 提权作用域内：放行显式合法租户，双缺失仍拒绝

```java
// ✅ 实体显式合法租户 → 放行并保留原值（上下文有没有租户都行）
TenantPrivilege.elevated(() -> {
    TestTenantNotePO po = new TestTenantNotePO();
    po.setId(100L);
    po.setTenantID("shop-B");        // 显式声明归属
    noteRepository.save(po);         // 落库为 shop-B
});

// ❌ 双缺失（实体无租户 + 上下文无租户）→ 依然拒绝，fail-closed 不回退
TenantPrivilege.elevated(() -> {
    TestTenantNotePO po = new TestTenantNotePO();   // tenantID 为 null
    noteRepository.save(po);
    // BusinessException: tenantID is required to inject into tenant-scoped write
});

// ❌ 实体携带 __MISSING_TENANT__ 占位 → 拒绝（占位值不是合法租户）
```

提权不是免检通道：**每一行数据的归属都必须显式写在实体上，或能从上下文注入**。
完整判定矩阵见 `TenantRepositoryAspectTest`（`crossTenantWrite*` 系列用例）。

### 3.3 flush 落库值

insert 时 Hibernate 层（`TenantIdGeneration`）的行为与门禁对齐：session 租户为 root
（提权下新建 session）时保留实体的显式 `tenantID`；普通租户 session 下显式值 ≠ session 租户
会抛 `PropertyValueException`。因此**不要绕过 repository 直接 `EntityManager.persist`
写 tenant-scoped 实体**——那会跳过 AOP 门禁的友好报错，直接撞上 ORM 层的异常。

---

## 4. 提权用法（TenantPrivilege）

### 4.1 API 与正确示例

```java
// 无返回值
TenantPrivilege.elevated(() -> orderRepository.deleteAll());

// 有返回值
long total = TenantPrivilege.elevated(() -> orderRepository.count());

// 先提权再开事务（推荐：消灭「作用域没罩住事务边界」的时序错误）
OrderResult result = TenantPrivilege.elevatedInTransaction(transactionTemplate, () -> {
    subOrderRepo.saveAll(List.of(
            subOrderOf("shop-A", ...),      // PO 显式 tenantID=shop-A
            subOrderOf("shop-B", ...)));    // PO 显式 tenantID=shop-B
    inventoryFacade.preoccupy(orderId, items);   // 多店库存预占
    return buildResult();
});
```

C 端跨店下单是典型形态：买家上下文**没有租户**，一次订单拆 N 个店铺子单——每行的归属写在
实体上，整段编排放进提权作用域。证例：
`crossTenantWriteShouldKeepExplicitTenantWhenContextTenantMissing`。

### 4.2 适用场景

只有三类场景允许提权，均需 code review 强审：

1. **平台运维**：superAdmin 查看/代管任意租户数据；
2. **系统编排**：支付回调、定时任务推进各店铺子单状态（actor 本身不属于任何租户）；
3. **C 端跨店编排**：买家一次动作触达多个店铺的数据。

常规业务 CRUD 一律不允许出现 `elevated`。

### 4.3 审计日志

每次进入作用域打一条 INFO 日志：

```text
[TenantPrivilege] elevated scope enter, action=XxxService$$Lambda$123, alreadyActive=false, at=2026-08-22T10:00:00Z
```

`alreadyActive=true` 表示嵌套提权，review 时应重点追问必要性。生产环境建议对该日志关键字
配置采集告警，做到「谁在什么时候进了提权」可追溯。

### 4.4 边界行为

- 出作用域自动恢复，异常透传且照样解绑
  （`TenantPrivilegeTest#callableExceptionPassesThroughAndUnbinds`）；
- 嵌套作用域逐层恢复
  （`TenantPrivilegeTest#nestedScopesRestoreLayerByLayer`）；
- 作用域内**无法**篡改绑定值（ScopedValue 语义），不存在「忘了退出」的状态污染。

---

## 5. 异步场景

### 5.1 快照只传身份三元组

`RequestContextPropagatingTaskDecorator` 在提交线程捕获 `ContextSnapshot`
（仅 `tenantID` / `role` / `identity`），worker 线程开头恢复、结尾清理：

```java
executor.setTaskDecorator(new RequestContextPropagatingTaskDecorator(tenantContextAccessor));
```

注意：装饰器需**逐 executor 手动绑定**，无自动配置。没绑定的线程池 = worker 无身份上下文 =
读全空、写全拒（fail-fast）。

### 5.2 提权状态不随线程传播

ScopedValue 默认不跨线程。父线程正在提权时提交的异步任务，worker 内
`TenantPrivilege.isActive()` 是 `false`
（`TenantPrivilegeTest#elevationDoesNotLeakIntoNewThread`）。
worker 内需要跨租户能力时必须在任务体内**显式声明**：

```java
// ❌ 幻觉：以为父线程的提权还在
CompletableFuture.runAsync(() -> orderRepository.findAll());   // 只看到 worker 自己租户（或空）

// ✅ worker 内显式声明模式与目标租户
CompletableFuture.runAsync(() ->
    TenantPrivilege.elevated(() -> {
        TestTenantNotePO po = new TestTenantNotePO();
        po.setTenantID(targetShopId);       // 目标租户必须显式给出
        ...
    }));
```

目标租户缺失时 worker 会 fail-fast（写入抛 `BusinessException`，读取返回空集），
不会静默落到错误的租户。

### 5.3 手动使用快照必须配对

不经 TaskDecorator 的自管线程（原生 `ExecutorService`、虚拟线程等）手动传播时，
`saveSnapshot` / `clearSnapshot` 必须配对，否则线程复用会串上下文：

```java
// ✅ 标准配对模板
TenantContextAccessor.ContextSnapshot snapshot = accessor.captureSnapshot();
executor.submit(() -> {
    TenantContextAccessor.saveSnapshot(snapshot);
    try {
        doWork();
    } finally {
        TenantContextAccessor.clearSnapshot();
    }
});
```

---

## 6. fail-closed 的空结果语义

上下文租户缺失时，resolver 返回占位值 `__MISSING_TENANT__`，filter 条件变成
`tenant_id = '__MISSING_TENANT__'`——**匹配不到任何行，查询静默返回空集而不是报错**
（Global 表不受影响）。这是设计行为，不是 bug。

```java
threadContext.setTenantID(null);
noteRepository.findAll();      // []     ← 库里有数据也查不到
noteRepository.findById(1L);   // Optional.empty
noteRepository.count();        // 0
```

**排障口诀：库里明明有数据却查不到，先查上下文，再怀疑数据。**

排查顺序：

1. 当前线程 `tenantContextAccessor.getTenantID()` 返回什么？
2. 若是异步 worker：executor 绑定 TaskDecorator 了吗？快照里的 `tenantID` 是 null 吗？
   `saveSnapshot` / `clearSnapshot` 配对了吗？
3. 若是 Web 请求：鉴权层往 `ThreadContext` 写租户了吗？

当前实现对此场景**没有专门的告警日志**（宁可静默也不中断正常请求流），上述三步是唯一的定位
手段；对隔离要求苛刻的项目可在 dev/test profile 自行加断言或日志增强。

证例：`tenantScopedQueryShouldBeFilteredAndFailClosedWhenTenantMissing`、
`globalQueryShouldNotBeFilteredWhenTenantMissing`。

---

## 7. open-in-view 保持关闭

`application.yml` 已显式关闭，**勿重新开启**：

```yaml
spring:
  jpa:
    # OSV 必须关闭：session 打开时一次性定型租户模式，打开时机必须可控
    open-in-view: false
```

原因：OSV 生效时 session 在「请求内第一次触碰数据库」的时刻才打开——这个时机可能早于你的
任何上下文准备逻辑（controller 预载、鉴权后置检查），租户模式就此随机定型。行为正确与否取决于
「路径上谁先碰到数据库」，这种时序耦合靠读代码守不住。此外 OSV 把 JDBC 连接占用从事务级膨胀到
请求级，高并发下连接池先行耗尽。

关闭后时序确定：准备上下文（含提权作用域）→ 事务开启 → session 打开 → resolver 定型。

---

## 8. 按主键加载的版本要求

`em.find()` / `findById` 应用 `_tenantId` 过滤是 HHH-16830 修复的能力，
**Hibernate ≤ 6.4 按主键加载不过滤**——意味着那个版本区间下 `findById` 能读到别的租户的行，
构成越权漏洞。

本仓库锁定 `hibernate-core 7.4.1.Final`（已包含修复），**禁止降级 Hibernate 版本**；
升级时须回归验证 `findById` 隔离（证例：`tenantScopedFindByIdShouldBeFiltered`）。

---

## 9. ORM 无关性

多租户机制的稳定契约只有两个，均不依赖 Hibernate：

- `TenantContextAccessor`：身份上下文唯一读取源（两级优先级：request scope → 线程快照）；
- `TenantPrivilege`：提权状态唯一判断源。

Hibernate 相关组件（resolver / `@TenantId` filter / AOP 门禁）只是契约的一层适配。
未来更换 MyBatis 等框架时：

1. 用 interceptor（MyBatis `Interceptor`）改写 SQL：所有 tenant-scoped 表自动追加
   `WHERE tenant_id = #{当前租户}`，租户取自 `TenantContextAccessor`；
2. insert 自动填充 `tenant_id` 列；缺失时的行为保持 fail-closed（追加恒假条件，返回空集）；
3. 写入门禁沿用 §3 的三条规则与提权矩阵，判断源用 `TenantPrivilege.isActive()`；
4. 业务代码零改动——只要业务侧只依赖上述两个契约类。

---

## 附录 A：常见症状速查表

| 症状 | 根因 | 修复 |
|------|------|------|
| 查询静默返回空集，库里有数据 | 上下文租户缺失（请求没设 / worker 无快照 / 快照 EMPTY），
  fail-closed 生效 | 按 §6 排查顺序检查 `getTenantID()`；绑 decorator、配对快照 |
| `PropertyValueException: assigned tenant id differs...` | session 已按具体租户定型，
  却以显式异租户实体走了 ORM 写入（绕过门禁或提权未生效） | 别绕过 repository；确认提权作用域
  罩住事务边界（§2.2 / §3.3） |
| 提权了但跨租户查询仍只见本租户 | `elevated` 写在了 `@Transactional` 方法内部，
  session 先于作用域定型 | 改用 `elevatedInTransaction` 或把 `elevated` 移到事务外层（§2.2） |
| 提权了但写入仍被拒 `cross-tenant write is forbidden` | 该路径不在提权作用域内
  （作用域提前退出了），或实体租户为 `__MISSING_TENANT__` 占位 | 检查作用域覆盖范围；
  实体显式设置合法租户，勿填占位值（§3.2） |
| 异步任务里查不到数据 / 写入抛 `tenantID is required...` | worker 无身份快照
  （decorator 未绑定）或提权未随线程传播而任务依赖它 | §5：绑定 decorator；任务体内显式
  `elevated` + 显式目标租户 |
| 线程池偶发串租户 | 手动 `saveSnapshot` / `clearSnapshot` 未配对，ThreadLocal 残留 | 按 §5.3
  配对模板改造，清理放进 `finally` |
| 升级/换依赖后 `findById` 能读到其他租户 | Hibernate 降级到 ≤ 6.4，HHH-16830 修复丢失 |
  锁定 ≥ 7.4.x 并回归 `tenantScopedFindByIdShouldBeFiltered`（§8） |
