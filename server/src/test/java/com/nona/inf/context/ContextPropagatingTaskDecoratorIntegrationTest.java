package com.nona.inf.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nona.ProjectApplication;
import com.nona.annotation.ScaffoldGenerated;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.core.task.TaskDecorator;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 验证 {@link ContextPropagatingTaskDecorator} 跨线程传播
 * 与 {@link ExecutionContextAccessor} 单级解析顺序（holder → boundSnapshot 回退，ScopedValue
 * 载体）：提交线程经 {@link ExecutionContext#withScope} 写入 holder，装饰器捕获快照，
 * worker 线程以双槽嵌套绑定（withSnapshot 外、withScope 内）还原。
 *
 * @author nona9961
 */
@SpringBootTest(classes = ProjectApplication.class)
@ActiveProfiles("test")
@ScaffoldGenerated
class ContextPropagatingTaskDecoratorIntegrationTest {

    @Autowired
    private ExecutionContextAccessor executionContextAccessor;

    private TaskDecorator taskDecorator;

    @BeforeEach
    void setUp() {
        taskDecorator = new ContextPropagatingTaskDecorator(executionContextAccessor);
    }

    /**
     * 验证装饰器从提交线程的 holder（{@link ExecutionContext#scope()}）捕获
     * tenantID / role / identity，并经 {@link ExecutionContextAccessor} 的 ScopedValue 快照回退
     * 在 worker 线程可见。
     */
    @Test
    void shouldPropagateContextSnapshotToWorkerThread() throws Exception {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTenantID("tenant-a");
            ExecutionContext.scope().setRole(List.of("admin", "editor"));
            ExecutionContext.scope().setIdentity("user-42");

            final CountDownLatch latch = new CountDownLatch(1);
            final AtomicReference<String> capturedTenant = new AtomicReference<>();
            final AtomicReference<List<String>> capturedRole = new AtomicReference<>();
            final AtomicReference<String> capturedIdentity = new AtomicReference<>();

            final Runnable task = () -> {
                capturedTenant.set(executionContextAccessor.getTenantID());
                capturedRole.set(executionContextAccessor.getRole());
                capturedIdentity.set(executionContextAccessor.getIdentity());
                latch.countDown();
            };

            final Runnable decorated = taskDecorator.decorate(task);
            final Thread worker = new Thread(decorated);
            worker.start();
            final boolean completed;
            try {
                completed = latch.await(5, TimeUnit.SECONDS);
                worker.join();
            }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while awaiting worker", e);
            }

            assertThat(completed).isTrue();
            assertThat(capturedTenant.get()).isEqualTo("tenant-a");
            assertThat(capturedRole.get()).containsExactly("admin", "editor");
            assertThat(capturedIdentity.get()).isEqualTo("user-42");
        });
    }

    /**
     * 验证 holder 优先于 boundSnapshot 回退：两者同时存在时，读取与捕获均取 holder 值
     * （单级解析第一顺位；worker 自身写入永不与外层快照冲突）。
     */
    @Test
    void shouldPreferHolderOverBoundSnapshotFallback() {
        final ContextSnapshot staleSnapshot = new ContextSnapshot(
                "from-fallback", List.of("visitor"), "fb-user");
        ExecutionContextAccessor.withSnapshot(staleSnapshot, () -> ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTenantID("from-request");
            ExecutionContext.scope().setRole(List.of("admin"));
            ExecutionContext.scope().setIdentity("req-user");

            assertThat(executionContextAccessor.getTenantID()).isEqualTo("from-request");

            final ContextSnapshot captured = executionContextAccessor.captureSnapshot();
            assertThat(captured.tenantID()).isEqualTo("from-request");
            assertThat(captured.role()).containsExactly("admin");
            assertThat(captured.identity()).isEqualTo("req-user");
        }));
    }

    /**
     * 验证 holder 缺失时 boundSnapshot 回退生效：无 withScope（纯 boundSnapshot 路径）
     * 绑定快照后读取到回退值。
     */
    @Test
    void shouldUseBoundSnapshotFallbackWhenHolderEmpty() {
        final ContextSnapshot snapshot = new ContextSnapshot(
                "fallback-tenant", List.of("visitor"), "fb-user-99");
        ExecutionContextAccessor.withSnapshot(snapshot, () -> {
            assertThat(executionContextAccessor.getTenantID()).isEqualTo("fallback-tenant");
        });
    }

    /**
     * 验证空快照视为无身份（fail-closed）：未绑定 / 绑定 {@link ContextSnapshot#EMPTY} /
     * 绑定空白 tenantID 快照 / withScope 空 holder（未写入），读取均返回 null。
     */
    @Test
    void emptySnapshotShouldYieldNullTenantID() {
        assertThat(executionContextAccessor.getTenantID()).isNull();

        ExecutionContextAccessor.withSnapshot(ContextSnapshot.EMPTY, () -> {
            assertThat(executionContextAccessor.getTenantID()).isNull();
        });

        ExecutionContextAccessor.withSnapshot(
                new ContextSnapshot("  ", null, null), () -> {
            assertThat(executionContextAccessor.getTenantID()).isNull();
        });

        ExecutionContext.withScope(() -> {
            assertThat(executionContextAccessor.getTenantID()).isNull();
        });
    }

    /**
     * 验证结构化作用域语义：withScope 内 holder 写入在任务执行期间可见
     * （decorate 捕获快照），作用域退出（含空 holder 场景）自动恢复
     * fail-closed——无需手动清理，线程上无残留。
     */
    @Test
    void shouldAutoRestoreSnapshotAfterScopeExit() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTenantID("tenant-x");

            final Runnable task = taskDecorator.decorate(() -> {
                assertThat(executionContextAccessor.getTenantID()).isEqualTo("tenant-x");
            });

            assertThat(executionContextAccessor.getTenantID()).isEqualTo("tenant-x");

            task.run();
        });

        ExecutionContext.withScope(() -> {
            assertThat(executionContextAccessor.getTenantID()).isNull();
        });
    }

    /**
     * 验证池化线程复用无残留：单线程池先后执行带快照与空 holder 作用域任务，
     * 第二个任务读不到第一个任务的租户（绑定随作用域退出自动消失）。
     */
    @Test
    void pooledThreadReuseShouldNotLeakSnapshot() throws Exception {
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ExecutionContext.withScope(() -> {
                ExecutionContext.scope().setTenantID("tenant-a");
                try {
                    pool.submit(taskDecorator.decorate(() -> {
                        assertThat(executionContextAccessor.getTenantID()).isEqualTo("tenant-a");
                    })).get();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while awaiting task", e);
                }
                catch (ExecutionException e) {
                    throw new IllegalStateException("task failed", e);
                }
            });

            ExecutionContext.withScope(() -> {
                final AtomicReference<String> captured = new AtomicReference<>();
                try {
                    pool.submit(taskDecorator.decorate(() -> captured.set(executionContextAccessor.getTenantID()))).get();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while awaiting task", e);
                }
                catch (ExecutionException e) {
                    throw new IllegalStateException("task failed", e);
                }

                assertThat(captured.get()).isNull();
            });
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 验证异常路径自动恢复：任务内抛异常后，同一池化线程再执行任务读不到残留快照。
     */
    @Test
    void exceptionPathShouldAutoRestoreBinding() throws Exception {
        final ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            ExecutionContext.withScope(() -> {
                ExecutionContext.scope().setTenantID("tenant-a");
                final Future<?> failing = pool.submit(taskDecorator.decorate(() -> {
                    throw new IllegalStateException("boom");
                }));
                assertThatThrownBy(failing::get).isInstanceOf(ExecutionException.class);
            });

            ExecutionContext.withScope(() -> {
                final AtomicReference<String> captured = new AtomicReference<>();
                try {
                    pool.submit(taskDecorator.decorate(() -> captured.set(executionContextAccessor.getTenantID()))).get();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while awaiting task", e);
                }
                catch (ExecutionException e) {
                    throw new IllegalStateException("task failed", e);
                }

                assertThat(captured.get()).isNull();
            });
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 验证嵌套异步传播：worker 内再派发任务时，captureSnapshot 回退读已绑定快照，
     * 内层 worker 继承外层租户视角（捕获不对称修复）。
     */
    @Test
    void nestedDispatchShouldInheritOuterSnapshot() throws Exception {
        final ExecutorService outer = Executors.newSingleThreadExecutor();
        final ExecutorService inner = Executors.newSingleThreadExecutor();
        try {
            ExecutionContext.withScope(() -> {
                ExecutionContext.scope().setTenantID("tenant-a");
                final AtomicReference<String> nestedCaptured = new AtomicReference<>();
                final AtomicReference<String> innerCaptured = new AtomicReference<>();
                try {
                    outer.submit(taskDecorator.decorate(() -> {
                        nestedCaptured.set(executionContextAccessor.captureSnapshot().tenantID());
                        try {
                            inner.submit(taskDecorator.decorate(
                                    () -> innerCaptured.set(executionContextAccessor.getTenantID()))).get();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException("nested dispatch interrupted", e);
                        } catch (ExecutionException e) {
                            throw new IllegalStateException("nested dispatch failed", e);
                        }
                    })).get();
                }
                catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("outer dispatch interrupted", e);
                }
                catch (ExecutionException e) {
                    throw new IllegalStateException("outer dispatch failed", e);
                }

                assertThat(nestedCaptured.get()).isEqualTo("tenant-a");
                assertThat(innerCaptured.get()).isEqualTo("tenant-a");
            });
        } finally {
            outer.shutdownNow();
            inner.shutdownNow();
        }
    }

    /**
     * 验证同线程嵌套绑定不串扰：内层绑定覆盖读取，退出后自动恢复外层绑定；
     * 全部退出后恢复 unbound（动态作用域语义）。
     */
    @Test
    void nestedScopeShouldRestoreOuterBinding() {
        final ContextSnapshot outerSnapshot = new ContextSnapshot(
                "outer-tenant", List.of("admin"), "outer-user");
        final ContextSnapshot innerSnapshot = new ContextSnapshot(
                "inner-tenant", List.of("visitor"), "inner-user");
        ExecutionContextAccessor.withSnapshot(outerSnapshot, () -> {
            assertThat(executionContextAccessor.getTenantID()).isEqualTo("outer-tenant");
            ExecutionContextAccessor.withSnapshot(innerSnapshot, () -> {
                assertThat(executionContextAccessor.getTenantID()).isEqualTo("inner-tenant");
            });
            assertThat(executionContextAccessor.getTenantID()).isEqualTo("outer-tenant");
        });

        assertThat(executionContextAccessor.getTenantID()).isNull();
    }
}