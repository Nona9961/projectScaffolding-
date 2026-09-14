package com.nona.inf.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.persistence.tracking.ChangeTrackerProvider;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ExecutionContext#withScope(Runnable)} 跟踪身份词法作用域场景测试：
 * 进入时快照当前 MDC 三键（不清空——自身无值的内层作用域继承外层视角），
 * 退出时（正常与异常路径）恢复进入时状态——栈语义，池化线程复用无残留。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：顶层作用域退出后三键清空（写者经持有者写入）</li>
 *   <li>Critical：嵌套作用域继承外层视角、内层写入覆盖、内层退出恢复外层；
 *       入口态为非空时退出恢复入口值；无入口态时退出移除；fail-closed 语义不变</li>
 *   <li>Fail：异常路径退出同样恢复（finally 语义）</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
class ExecutionContextMdcLifecycleUnitTest {

    /**
     * 无配置提供者：仅用于 fail-closed 断言（未绑定作用域时 tracker() 在触及提供者前抛出）。
     */
    private static final ChangeTrackerProvider PROVIDER =
            new ChangeTrackerProvider(Map.of(), Set.of(), Set.of());

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
     * H1（顶层作用域无残留）：作用域内写入跟踪身份 → 退出后三键清空——
     * 词法作用域边界即 MDC 生命周期边界。
     */
    @Test
    void shouldClearMdcAfterTopLevelScopeExits() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-a", "span-a", "01"));

            assertThat(ThreadContext.get("trace_id")).isEqualTo("trace-a");
            assertThat(ThreadContext.get("span_id")).isEqualTo("span-a");
            assertThat(ThreadContext.get("trace_flags")).isEqualTo("01");
        });

        assertThat(ThreadContext.get("trace_id")).isNull();
        assertThat(ThreadContext.get("span_id")).isNull();
        assertThat(ThreadContext.get("trace_flags")).isNull();
    }

    // ========== Critical path ==========

    /**
     * C1（嵌套栈语义）：外层写入后进入内层——内层无自身写入时继承外层视角；
     * 内层写入覆盖三键；内层退出恢复外层值；外层退出清空。
     */
    @Test
    void shouldRestoreOuterMdcAfterInnerScopeExits() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("outer-t", "outer-s", "00"));

            ExecutionContext.withScope(() -> {
                assertThat(ThreadContext.get("trace_id")).isEqualTo("outer-t");
                assertThat(ThreadContext.get("span_id")).isEqualTo("outer-s");
                assertThat(ThreadContext.get("trace_flags")).isEqualTo("00");

                ExecutionContext.scope().setTraceIdentity(
                        new TraceIdentity("inner-t", "inner-s", "01"));

                assertThat(ThreadContext.get("trace_id")).isEqualTo("inner-t");
                assertThat(ThreadContext.get("span_id")).isEqualTo("inner-s");
                assertThat(ThreadContext.get("trace_flags")).isEqualTo("01");
            });

            assertThat(ThreadContext.get("trace_id")).isEqualTo("outer-t");
            assertThat(ThreadContext.get("span_id")).isEqualTo("outer-s");
            assertThat(ThreadContext.get("trace_flags")).isEqualTo("00");
        });

        assertThat(ThreadContext.get("trace_id")).isNull();
        assertThat(ThreadContext.get("span_id")).isNull();
        assertThat(ThreadContext.get("trace_flags")).isNull();
    }

    /**
     * C2（入口缺失 → 退出移除）：作用域动作直接改 MDC（非持有者写入路径）时，
     * 退出仍恢复入口缺失态——栈语义按入口快照恢复，不依赖写入来源。
     */
    @Test
    void shouldRemoveMdcWrittenInsideScopeWhenEntryStateWasEmpty() {
        ExecutionContext.withScope(() -> ThreadContext.put("trace_id", "inner-trace"));

        assertThat(ThreadContext.get("trace_id")).isNull();
    }

    /**
     * C3（入口存在 → 退出恢复）：进入作用域前线程已有 MDC（外层视角），
     * 作用域动作覆盖后退出恢复入口值（present → put back）。
     */
    @Test
    void shouldRestorePresentEntryMdcAfterScopeActionOverwritesIt() {
        ThreadContext.put("trace_id", "entry-trace");
        ThreadContext.put("span_id", "entry-span");
        ThreadContext.put("trace_flags", "00");

        ExecutionContext.withScope(() -> {
            ThreadContext.put("trace_id", "inner-trace");
            ThreadContext.put("span_id", "inner-span");
            ThreadContext.put("trace_flags", "01");

            assertThat(ThreadContext.get("trace_id")).isEqualTo("inner-trace");
        });

        assertThat(ThreadContext.get("trace_id")).isEqualTo("entry-trace");
        assertThat(ThreadContext.get("span_id")).isEqualTo("entry-span");
        assertThat(ThreadContext.get("trace_flags")).isEqualTo("00");
    }

    /**
     * C4（池化线程复用无残留）：worker 线程内经 withScope 写入跟踪身份、作用域内可见；
     * 任务结束后同一 worker 复用读取无残留（无需容器，普通单线程池）。
     */
    @Test
    void shouldLeaveNoMdcResidueOnReusedWorkerThread() throws Exception {
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            final AtomicReference<String> visibleInWorker = new AtomicReference<>();
            final Future<?> task = pool.submit(() -> ExecutionContext.withScope(() -> {
                ExecutionContext.scope().setTraceIdentity(
                        new TraceIdentity("worker-t", "worker-s", "01"));
                visibleInWorker.set(ThreadContext.get("trace_id"));
            }));
            task.get(5, TimeUnit.SECONDS);

            final AtomicReference<String> residue = new AtomicReference<>();
            final AtomicReference<String> spanResidue = new AtomicReference<>();
            final AtomicReference<String> flagsResidue = new AtomicReference<>();
            pool.submit(() -> {
                residue.set(ThreadContext.get("trace_id"));
                spanResidue.set(ThreadContext.get("span_id"));
                flagsResidue.set(ThreadContext.get("trace_flags"));
            }).get(5, TimeUnit.SECONDS);

            assertThat(visibleInWorker.get()).isEqualTo("worker-t");
            assertThat(residue.get()).isNull();
            assertThat(spanResidue.get()).isNull();
            assertThat(flagsResidue.get()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * C5（fail-closed 不变）：桥接不得弱化作用域契约——作用域内写入跟踪身份后退出，
     * tracker() 仍 fail-closed 抛 {@link IllegalStateException}、scope() 归 null、MDC 无残留。
     */
    @Test
    void shouldKeepTrackerFailClosedAndScopeUnboundAfterTraceWrite() {
        ExecutionContext.withScope(() -> {
            assertThat(ExecutionContext.scope()).isNotNull();
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-c", "span-c", "01"));
        });

        assertThat(ExecutionContext.scope()).isNull();
        assertThatThrownBy(() -> ExecutionContext.tracker(PROVIDER))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ThreadContext.get("trace_id")).isNull();
        assertThat(ThreadContext.get("span_id")).isNull();
        assertThat(ThreadContext.get("trace_flags")).isNull();
    }

    // ========== Fail path ==========

    /**
     * F1（异常路径无残留）：作用域内写入后抛异常——异常向调用方传播，
     * 三键随作用域退出恢复（finally 语义），线程无残留。
     */
    @Test
    void shouldClearMdcAfterExceptionPathScopeExits() {
        assertThatThrownBy(() -> ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-b", "span-b", "01"));
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(ThreadContext.get("trace_id")).isNull();
        assertThat(ThreadContext.get("span_id")).isNull();
        assertThat(ThreadContext.get("trace_flags")).isNull();
    }
}
