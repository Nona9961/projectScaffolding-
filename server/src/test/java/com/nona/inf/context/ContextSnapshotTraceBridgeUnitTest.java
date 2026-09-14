package com.nona.inf.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.context.TenantContextAccessor.ContextSnapshot;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

/**
 * {@link ContextSnapshot} 跟踪身份传播面场景测试：提交线程捕获（持有者 → 已绑定快照回退）
 * 与 worker 侧重放（{@link TenantContextAccessor#withSnapshot} 绑定即同步 MDC 三键、
 * 退出恢复入口状态）。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：快照携带持有者写入的跟踪身份；withSnapshot 作用域内三键可见、退出清空</li>
 *   <li>Critical：持有者未写时回退已绑定快照的跟踪身份；持有者写入优先于回退；
 *       withSnapshot 退出恢复入口 MDC 状态</li>
 *   <li>边界（回归锁）：快照无跟踪身份时 withSnapshot 不得清空线程既有 MDC；
 *       既有三元组构造器（3 参 / 4 参）语义不变（跟踪身份缺省为 null）</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
class ContextSnapshotTraceBridgeUnitTest {

    /**
     * 被测访问器：无参构造（纯 JUnit 形态，不经 Spring 装配）。
     */
    private final TenantContextAccessor accessor = new TenantContextAccessor();

    /**
     * 用例后清理 MDC 三键：ThreadContext 为线程局部且 surefire 复用 fork 线程，
     * 失败用例（含红阶段停在半途的用例）不得把键残留给后续用例。
     */
    @AfterEach
    void clearTraceMdcKeys() {
        ThreadContext.remove("trace_id");
        ThreadContext.remove("span_id");
        ThreadContext.remove("trace_flags");
    }

    // ========== Happy path ==========

    /**
     * H1（持有者捕获）：作用域内写入跟踪身份 → captureSnapshot 整体携带该值
     * （提交线程视角，供 worker 重放）。
     */
    @Test
    void shouldCaptureTraceIdentityFromScopeHolder() {
        TrackingContext.withScope(() -> {
            final TrackingScope.TraceIdentity identity =
                    new TrackingScope.TraceIdentity("capture-t", "capture-s", "01");
            TrackingContext.scope().setTraceIdentity(identity);

            assertThat(accessor.captureSnapshot().traceIdentity()).isEqualTo(identity);
        });
    }

    /**
     * H2（绑定即重放）：withSnapshot 绑定携带跟踪身份的快照 → 作用域内三键可见；
     * 退出后三键清空（入口缺失态恢复）。
     */
    @Test
    void shouldExposeSnapshotTraceIdentityInsideWithSnapshotAndClearAfterExit() {
        final ContextSnapshot snapshot = new ContextSnapshot(
                "tenant-a", List.of("admin"), "user-1", null,
                new TrackingScope.TraceIdentity("replay-t", "replay-s", "01"));

        TenantContextAccessor.withSnapshot(snapshot, () -> {
            assertThat(ThreadContext.get("trace_id")).isEqualTo("replay-t");
            assertThat(ThreadContext.get("span_id")).isEqualTo("replay-s");
            assertThat(ThreadContext.get("trace_flags")).isEqualTo("01");
        });

        assertThat(ThreadContext.get("trace_id")).isNull();
        assertThat(ThreadContext.get("span_id")).isNull();
        assertThat(ThreadContext.get("trace_flags")).isNull();
    }

    // ============ Critical path ==========

    /**
     * C1（嵌套异步回退）：持有者未写、已绑定快照携带跟踪身份 → 捕获回退快照视角
     * （worker 内再派发继承外层视角；整体回退，不做字段级拼接）。
     */
    @Test
    void shouldFallBackToBoundSnapshotTraceIdentityWhenHolderHasNone() {
        final TrackingScope.TraceIdentity boundIdentity =
                new TrackingScope.TraceIdentity("bound-t", "bound-s", "00");
        final ContextSnapshot bound = new ContextSnapshot(
                "tenant-a", List.of("admin"), "user-1", null, boundIdentity);

        TenantContextAccessor.withSnapshot(bound, () -> TrackingContext.withScope(() ->
                assertThat(accessor.captureSnapshot().traceIdentity()).isEqualTo(boundIdentity)));
    }

    /**
     * C2（持有者优先）：持有者与已绑定快照同时携带跟踪身份时，捕获取持有者值
     * （与三元组解析同序：holder 优先）。
     */
    @Test
    void shouldPreferHolderTraceIdentityOverBoundSnapshot() {
        final ContextSnapshot bound = new ContextSnapshot(
                "tenant-a", List.of("admin"), "user-1", null,
                new TrackingScope.TraceIdentity("bound-t", "bound-s", "00"));

        TenantContextAccessor.withSnapshot(bound, () -> TrackingContext.withScope(() -> {
            final TrackingScope.TraceIdentity holderIdentity =
                    new TrackingScope.TraceIdentity("holder-t", "holder-s", "01");
            TrackingContext.scope().setTraceIdentity(holderIdentity);

            assertThat(accessor.captureSnapshot().traceIdentity()).isEqualTo(holderIdentity);
        }));
    }

    /**
     * C3（入口态恢复）：绑定前线程已有 MDC（外层视角）→ withSnapshot 作用域内重放快照值，
     * 退出恢复入口值（栈语义）。
     */
    @Test
    void shouldRestorePriorMdcStateAfterWithSnapshotExits() {
        ThreadContext.put("trace_id", "outer-t");
        ThreadContext.put("span_id", "outer-s");
        ThreadContext.put("trace_flags", "00");

        final ContextSnapshot snapshot = new ContextSnapshot(
                null, null, null, null,
                new TrackingScope.TraceIdentity("replay-t", "replay-s", "01"));

        TenantContextAccessor.withSnapshot(snapshot, () -> {
            assertThat(ThreadContext.get("trace_id")).isEqualTo("replay-t");
            assertThat(ThreadContext.get("span_id")).isEqualTo("replay-s");
            assertThat(ThreadContext.get("trace_flags")).isEqualTo("01");
        });

        assertThat(ThreadContext.get("trace_id")).isEqualTo("outer-t");
        assertThat(ThreadContext.get("span_id")).isEqualTo("outer-s");
        assertThat(ThreadContext.get("trace_flags")).isEqualTo("00");
    }

    // ========== 回归锁（既有语义不因新增跟踪身份槽而改变） ==========

    /**
     * G1（无身份不清空）：快照无跟踪身份时 withSnapshot 不得清空线程既有 MDC——
     * 内层绑定无自身值时继承外层视角（与 withScope「进入不清空」同规则）。
     */
    @Test
    void shouldKeepPriorMdcStateWhenSnapshotCarriesNoTraceIdentity() {
        ThreadContext.put("trace_id", "outer-t");

        TenantContextAccessor.withSnapshot(ContextSnapshot.EMPTY, () ->
                assertThat(ThreadContext.get("trace_id")).isEqualTo("outer-t"));
    }

    /**
     * G2（旧构造器兼容）：既有三元组构造器（3 参 / 4 参）与 EMPTY 语义不变——
     * 跟踪身份缺省为 {@code null}，既有调用方无需改动。
     */
    @Test
    void shouldKeepLegacySnapshotConstructorsTraceIdentityFree() {
        final ContextSnapshot threeArg = new ContextSnapshot("tenant-a", List.of("admin"), "user-1");
        final ContextSnapshot fourArg =
                new ContextSnapshot("tenant-a", List.of("admin"), "user-1", null);

        assertThat(threeArg.traceIdentity()).isNull();
        assertThat(fourArg.traceIdentity()).isNull();
        assertThat(ContextSnapshot.EMPTY.traceIdentity()).isNull();
    }
}
