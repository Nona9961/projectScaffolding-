package com.nona.inf.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nona.annotation.ScaffoldGenerated;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * MDC 桥接跨线程装配面测试：{@link ContextPropagatingTaskDecorator} 按下游项目
 * 接入形态绑定到真实 {@link ThreadPoolTaskExecutor}（单线程池 = 池化线程复用场景），
 * 提交线程 holder 写入的跟踪身份随任务传播到 worker 线程，任务结束后池化线程无残留
 * （含异常路径）。
 * <p>
 * 面内（真实装配，mock 测不到）：装饰器捕获（holder → 已绑定快照回退）→
 * worker 双槽绑定（withSnapshot 外、withScope 内）→ withSnapshot 重放 MDC 三键 →
 * worker 任务可见 → 作用域退出回收；第二次任务复用同一池化线程读不到前次身份。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：提交线程写入的跟踪身份在 worker 任务内三键可见</li>
 *   <li>Critical：任务结束后同一池化线程复用无残留（第二次任务读不到前次值）</li>
 *   <li>Fail：worker 任务异常后池化线程同样无残留</li>
 *   <li>边界（回归锁）：提交线程无跟踪身份时 worker 不得凭空出现三键</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
class MdcBridgeCrossThreadIntegrationTest {

    /**
     * 单次任务等待上限：相对窗口（秒），不使用绝对时间。
     */
    private static final long AWAIT_SECONDS = 5L;

    private ThreadPoolTaskExecutor executor;

    /**
     * 等待任务完成并按统一语义处理中断 / 执行失败 / 超时。
     *
     * @param future 待等待的任务句柄
     */
    private static void await(Future<?> future) {
        try {
            future.get(AWAIT_SECONDS, TimeUnit.SECONDS);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("task interrupted", e);
        }
        catch (ExecutionException e) {
            throw new IllegalStateException("task failed", e);
        }
        catch (TimeoutException e) {
            throw new IllegalStateException("task timed out after " + AWAIT_SECONDS + "s", e);
        }
    }

    /**
     * 每用例重建装配：单线程池（core = max = 1，池化线程复用形态）+ 装饰器接入
     * （下游项目注册形态）；线程池与装饰器均为新实例，用例间零共享状态。
     */
    @BeforeEach
    void setUp() {
        final ExecutionContextAccessor accessor = new ExecutionContextAccessor();
        final ContextPropagatingTaskDecorator decorator =
                new ContextPropagatingTaskDecorator(accessor);
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(decorator);
        executor.initialize();
        clearTraceMdcKeys();
    }

    /**
     * 用例后关停线程池并清理测试线程 MDC 三键（ThreadContext 为线程局部，
     * surefire 复用 fork 线程）。
     */
    @AfterEach
    void tearDown() {
        executor.shutdown();
        clearTraceMdcKeys();
    }

    /**
     * 清理 MDC 三键（测试线程卫生：失败用例不得把键残留给后续用例）。
     */
    private static void clearTraceMdcKeys() {
        ThreadContext.remove("trace_id");
        ThreadContext.remove("span_id");
        ThreadContext.remove("trace_flags");
    }

    // ========== Happy path ==========

    /**
     * H1（传播可见）：提交线程作用域内写入跟踪身份 → 装饰后的任务在 worker 线程内三键可见、
     * 值一致；提交线程退出作用域后自身无残留。
     */
    @Test
    void shouldPropagateTraceIdentityToPooledWorker() {
        final AtomicReference<Thread> workerThread = new AtomicReference<>();
        final AtomicReference<String> workerTrace = new AtomicReference<>();
        final AtomicReference<String> workerSpan = new AtomicReference<>();
        final AtomicReference<String> workerFlags = new AtomicReference<>();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-x", "span-x", "01"));

            await(executor.submit(() -> {
                workerThread.set(Thread.currentThread());
                workerTrace.set(ThreadContext.get("trace_id"));
                workerSpan.set(ThreadContext.get("span_id"));
                workerFlags.set(ThreadContext.get("trace_flags"));
            }));
        });

        assertThat(workerThread.get()).isNotSameAs(Thread.currentThread());
        assertThat(workerTrace.get()).isEqualTo("trace-x");
        assertThat(workerSpan.get()).isEqualTo("span-x");
        assertThat(workerFlags.get()).isEqualTo("01");
        assertThat(ThreadContext.get("trace_id")).isNull();
        assertThat(ThreadContext.get("span_id")).isNull();
        assertThat(ThreadContext.get("trace_flags")).isNull();
    }

    // ========== Critical path ==========

    /**
     * C1（池化线程复用无残留）：带跟踪身份的任务执行后，同一池化线程上再执行
     * 无跟踪身份来源的任务——读不到前次身份（绑定随任务结束回收），且两任务确为同一线程。
     */
    @Test
    void shouldNotLeakTraceIdentityToSecondTaskOnReusedPooledThread() {
        final AtomicReference<Thread> firstWorker = new AtomicReference<>();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-y", "span-y", "01"));
            await(executor.submit(() -> firstWorker.set(Thread.currentThread())));
        });

        final AtomicReference<Thread> probeWorker = new AtomicReference<>();
        final AtomicReference<String> probeTrace = new AtomicReference<>();
        final AtomicReference<String> probeSpan = new AtomicReference<>();
        final AtomicReference<String> probeFlags = new AtomicReference<>();

        ExecutionContext.withScope(() -> await(executor.submit(() -> {
            probeWorker.set(Thread.currentThread());
            probeTrace.set(ThreadContext.get("trace_id"));
            probeSpan.set(ThreadContext.get("span_id"));
            probeFlags.set(ThreadContext.get("trace_flags"));
        })));

        assertThat(probeWorker.get()).isSameAs(firstWorker.get());
        assertThat(probeTrace.get()).isNull();
        assertThat(probeSpan.get()).isNull();
        assertThat(probeFlags.get()).isNull();
    }

    // ========== Fail path ==========

    /**
     * F1（异常路径回收）：worker 任务内抛异常（经 Future 传播为 {@link ExecutionException}）后，
     * 同一池化线程复用读不到残留三键。
     */
    @Test
    void shouldCleanUpTraceIdentityAfterExceptionPathTask() {
        final AtomicReference<Thread> failingWorker = new AtomicReference<>();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(
                    new TraceIdentity("trace-z", "span-z", "01"));
            final Runnable failingTask = () -> {
                failingWorker.set(Thread.currentThread());
                throw new IllegalStateException("boom");
            };
            final Future<?> failing = executor.submit(failingTask);
            assertThatThrownBy(() -> failing.get(AWAIT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(IllegalStateException.class);
        });

        final AtomicReference<Thread> probeWorker = new AtomicReference<>();
        final AtomicReference<String> probeTrace = new AtomicReference<>();
        final AtomicReference<String> probeSpan = new AtomicReference<>();
        final AtomicReference<String> probeFlags = new AtomicReference<>();

        ExecutionContext.withScope(() -> await(executor.submit(() -> {
            probeWorker.set(Thread.currentThread());
            probeTrace.set(ThreadContext.get("trace_id"));
            probeSpan.set(ThreadContext.get("span_id"));
            probeFlags.set(ThreadContext.get("trace_flags"));
        })));

        assertThat(probeWorker.get()).isSameAs(failingWorker.get());
        assertThat(probeTrace.get()).isNull();
        assertThat(probeSpan.get()).isNull();
        assertThat(probeFlags.get()).isNull();
    }

    // ========== 回归锁 ==========

    /**
     * G1（无身份不凭空注入）：提交线程无跟踪身份时，worker 任务内三键保持缺失——
     * 桥接不得在无来源时写入 MDC。
     */
    @Test
    void shouldKeepWorkerMdcEmptyWhenSubmitterHasNoTraceIdentity() {
        final AtomicReference<String> workerTrace = new AtomicReference<>();
        final AtomicReference<String> workerSpan = new AtomicReference<>();
        final AtomicReference<String> workerFlags = new AtomicReference<>();

        ExecutionContext.withScope(() -> await(executor.submit(() -> {
            workerTrace.set(ThreadContext.get("trace_id"));
            workerSpan.set(ThreadContext.get("span_id"));
            workerFlags.set(ThreadContext.get("trace_flags"));
        })));

        assertThat(workerTrace.get()).isNull();
        assertThat(workerSpan.get()).isNull();
        assertThat(workerFlags.get()).isNull();
    }
}
