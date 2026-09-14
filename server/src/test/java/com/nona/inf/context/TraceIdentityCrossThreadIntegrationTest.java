package com.nona.inf.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.logging.IsolatedLogTestSupport;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 跨线程跟踪身份记录字段装配面测试：{@link ContextPropagatingTaskDecorator} 按下游项目
 * 接入形态绑定到真实 {@link ThreadPoolTaskExecutor}（单线程池 = 池化线程复用场景），
 * 提交线程作用域身份随任务传播到 worker 线程——断言锚定 worker 内写日志落盘记录的
 * trace 字段（日志侧拉取模式）。
 * <p>
 * 面内（真实装配，mock 测不到）：装饰器捕获（holder → 已绑定快照回退）→ worker 双槽绑定
 * （withSnapshot 外、withScope 内）→ worker 作用域继承快照身份 → worker 事件创建时拉取
 * → 记录带三字段；作用域退出回收后第二次任务读不到前次身份。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：提交线程写入的身份在 worker 记录中可见</li>
 *   <li>Critical：任务结束后同一池化线程复用无残留（第二次任务记录省略三字段）</li>
 *   <li>Fail：worker 任务异常后池化线程同样无残留</li>
 *   <li>边界（回归锁）：提交线程无身份时 worker 记录不得凭空出现三字段</li>
 * </ul>
 * <p>
 * 记录来自每用例独立的隔离日志上下文（{@code LOG_PATH} 重定向到 {@link TempDir}），
 * 轮询等待落盘，禁止固定睡眠。
 *
 * @author nona9961
 */
@ScaffoldGenerated
class TraceIdentityCrossThreadIntegrationTest {

    /**
     * 探针 logger 名：记录归属标识。
     */
    private static final String PROBE_LOGGER = "com.nona.inf.context.TraceIdentityCrossThreadIntegrationTest";

    /**
     * 单次任务等待上限：相对窗口（秒），不使用绝对时间。
     */
    private static final long AWAIT_SECONDS = 5L;

    /**
     * 按用例隔离的日志目录。
     */
    @TempDir
    Path logDir;

    private LoggerContext loggerContext;

    private ThreadPoolTaskExecutor executor;

    // ========== fixtures ==========

    /**
     * 每用例重建装配：隔离日志上下文 + 单线程池（core = max = 1，池化线程复用形态）+
     * 装饰器接入（下游项目注册形态）；线程池与装饰器均为新实例，用例间零共享状态。
     *
     * @throws Exception 隔离日志上下文装载失败
     */
    @BeforeEach
    void setUp() throws Exception {
        final ExecutionContextAccessor accessor = new ExecutionContextAccessor();
        final ContextPropagatingTaskDecorator decorator =
                new ContextPropagatingTaskDecorator(accessor);
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setTaskDecorator(decorator);
        executor.initialize();
        loggerContext = IsolatedLogTestSupport.startIsolatedContext("trace-identity-cross-thread", logDir);
    }

    /**
     * 用例后关停线程池并释放隔离日志上下文；装配前置失败时上下文尚未创建，跳过。
     */
    @AfterEach
    void tearDown() {
        executor.shutdown();
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

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
     * 生成用例唯一探针标记。
     *
     * @return 探针标记
     */
    private static String newMarker() {
        return "trace-probe-" + UUID.randomUUID();
    }

    // ========== Happy path ==========

    /**
     * H1（传播可见）：提交线程作用域内写入跟踪身份 → 装饰后的任务在 worker 线程写日志，
     * 记录三字段取值与提交线程身份一致，且任务确在另一线程执行。
     */
    @Test
    void shouldRecordTraceFieldsOnPooledWorkerFromSubmitterIdentity() throws Exception {
        final TraceIdentity identity = new TraceIdentity("trace-x", "span-x", "01");
        final String marker = newMarker();
        final AtomicReference<Thread> workerThread = new AtomicReference<>();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(identity);
            await(executor.submit(() -> {
                workerThread.set(Thread.currentThread());
                loggerContext.getLogger(PROBE_LOGGER).info(marker);
            }));
        });

        assertThat(workerThread.get()).isNotSameAs(Thread.currentThread());
        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsPresent(record, identity);
    }

    // ========== Critical path ==========

    /**
     * C1（池化线程复用无残留）：带跟踪身份的任务在 worker 记录三字段后，同一池化线程上
     * 再执行无跟踪身份来源的任务——记录省略三字段，且两任务确为同一线程。
     */
    @Test
    void shouldNotLeakTraceFieldsToSecondTaskOnReusedPooledThread() throws Exception {
        final TraceIdentity identity = new TraceIdentity("trace-y", "span-y", "01");
        final String firstMarker = newMarker();
        final String probeMarker = newMarker();
        final AtomicReference<Thread> firstWorker = new AtomicReference<>();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(identity);
            await(executor.submit(() -> {
                firstWorker.set(Thread.currentThread());
                loggerContext.getLogger(PROBE_LOGGER).info(firstMarker);
            }));
        });
        IsolatedLogTestSupport.assertTraceFieldsPresent(
                IsolatedLogTestSupport.awaitRecord(logDir, firstMarker), identity);

        final AtomicReference<Thread> probeWorker = new AtomicReference<>();
        ExecutionContext.withScope(() -> await(executor.submit(() -> {
            probeWorker.set(Thread.currentThread());
            loggerContext.getLogger(PROBE_LOGGER).info(probeMarker);
        })));

        assertThat(probeWorker.get()).isSameAs(firstWorker.get());
        IsolatedLogTestSupport.assertTraceFieldsAbsent(
                IsolatedLogTestSupport.awaitRecord(logDir, probeMarker));
    }

    // ========== Fail path ==========

    /**
     * F1（异常路径回收）：worker 任务内抛异常（经 Future 传播为 {@link ExecutionException}）后，
     * 同一池化线程复用写出的记录省略三字段。
     */
    @Test
    void shouldCleanUpTraceFieldsAfterExceptionPathTask() throws Exception {
        final TraceIdentity identity = new TraceIdentity("trace-z", "span-z", "01");
        final String probeMarker = newMarker();
        final AtomicReference<Thread> failingWorker = new AtomicReference<>();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(identity);
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
        ExecutionContext.withScope(() -> await(executor.submit(() -> {
            probeWorker.set(Thread.currentThread());
            loggerContext.getLogger(PROBE_LOGGER).info(probeMarker);
        })));

        assertThat(probeWorker.get()).isSameAs(failingWorker.get());
        IsolatedLogTestSupport.assertTraceFieldsAbsent(
                IsolatedLogTestSupport.awaitRecord(logDir, probeMarker));
    }

    // ========== 回归锁 ==========

    /**
     * G1（无身份不凭空注入）：提交线程无跟踪身份时，worker 记录保持省略三字段——
     * 拉取方不得在无来源时注入。
     */
    @Test
    void shouldKeepWorkerRecordTraceFreeWhenSubmitterHasNoIdentity() throws Exception {
        final String marker = newMarker();

        ExecutionContext.withScope(() -> await(executor.submit(() ->
                loggerContext.getLogger(PROBE_LOGGER).info(marker))));

        IsolatedLogTestSupport.assertTraceFieldsAbsent(IsolatedLogTestSupport.awaitRecord(logDir, marker));
    }
}
