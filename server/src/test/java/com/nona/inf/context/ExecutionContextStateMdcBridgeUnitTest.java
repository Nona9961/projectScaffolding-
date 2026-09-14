package com.nona.inf.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.nona.annotation.ScaffoldGenerated;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * {@link ExecutionContextState} 跟踪身份写入面场景测试：持有者写入即原子同步 MDC 三键
 * （{@code trace_id} / {@code span_id} / {@code trace_flags}），显式清除即三键整体移除。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：setTraceIdentity 后三键立即可见；持有者可读回同一跟踪身份</li>
 *   <li>Critical：显式清除（传 null）→ 三键整体移除，不残留任一键</li>
 *   <li>Fail：部分三元组（任一字段为 null）构造被拒绝——不存在「半个跟踪身份」</li>
 *   <li>边界：从未写入跟踪身份的作用域不注入任何 MDC 键</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
class ExecutionContextStateMdcBridgeUnitTest {

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
     * H1（写入可见）：作用域内写入跟踪身份后，三个 MDC 键立即可见且取值一致——
     * 键名即日志布局的消费契约，不得改名。
     */
    @Test
    void shouldExposeTripleInMdcWhenWrittenToScope() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-1", "span-1", "01"));

            assertThat(ThreadContext.get("trace_id")).isEqualTo("trace-1");
            assertThat(ThreadContext.get("span_id")).isEqualTo("span-1");
            assertThat(ThreadContext.get("trace_flags")).isEqualTo("01");
        });
    }

    /**
     * H2（持有者承载）：写入的跟踪身份在持有者可读回（值等价），供快照捕获路径消费。
     */
    @Test
    void shouldRetainTraceIdentityOnHolder() {
        ExecutionContext.withScope(() -> {
            final TraceIdentity identity =
                    new TraceIdentity("trace-2", "span-2", "00");
            ExecutionContext.scope().setTraceIdentity(identity);

            assertThat(ExecutionContext.scope().getTraceIdentity()).isEqualTo(identity);
        });
    }

    // ========== Critical path ==========

    /**
     * C1（显式清除）：先写入再传 null——三键整体移除（非部分清除），任一键都不残留。
     */
    @Test
    void shouldRemoveAllThreeMdcKeysWhenTraceIdentityClearedExplicitly() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-3", "span-3", "01"));

            ExecutionContext.scope().setTraceIdentity(null);

            assertThat(ThreadContext.get("trace_id")).isNull();
            assertThat(ThreadContext.get("span_id")).isNull();
            assertThat(ThreadContext.get("trace_flags")).isNull();
        });
    }

    /**
     * C2（边界：无写入不注入）：作用域内从未写跟踪身份时三键保持缺失——
     * 桥接不得凭空写入 MDC（回归锁：既有空作用域语义不变）。
     */
    @Test
    void shouldNotInjectMdcKeysWhenNoTraceIdentityWritten() {
        ExecutionContext.withScope(() -> {
            assertThat(ThreadContext.get("trace_id")).isNull();
            assertThat(ThreadContext.get("span_id")).isNull();
            assertThat(ThreadContext.get("trace_flags")).isNull();
        });
    }

    // ========== Fail path ==========

    /**
     * F1（部分三元组拒绝）：任一字段为 null 的构造被拒绝——跟踪身份要么整体存在、
     * 要么完全缺失，不允许产生部分更新窗口。
     */
    @Test
    void shouldRejectPartialTraceIdentityWithNullComponent() {
        assertThatNullPointerException().isThrownBy(
                () -> new TraceIdentity(null, "span-4", "01"));
        assertThatNullPointerException().isThrownBy(
                () -> new TraceIdentity("trace-4", null, "01"));
        assertThatNullPointerException().isThrownBy(
                () -> new TraceIdentity("trace-4", "span-4", null));
    }
}
