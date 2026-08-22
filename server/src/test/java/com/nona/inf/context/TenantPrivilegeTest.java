package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.exceptions.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link TenantPrivilege} 单测：纯 JUnit5 + AssertJ，不依赖 Spring。
 *
 * @author nona9961
 */
@ScaffoldGenerated
class TenantPrivilegeTest {

    @Test
    void runnableScopeActivatesAndRestores() {
        AtomicBoolean inside = new AtomicBoolean(false);

        TenantPrivilege.elevated(() -> inside.set(TenantPrivilege.isActive()));

        assertThat(inside).isTrue();
        assertThat(TenantPrivilege.isActive()).isFalse();
    }

    @Test
    void callableScopeReturnsValue() throws Exception {
        String result = TenantPrivilege.elevated(() -> {
            assertThat(TenantPrivilege.isActive()).isTrue();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(TenantPrivilege.isActive()).isFalse();
    }

    @Test
    void callableExceptionPassesThroughAndUnbinds() {
        assertThatThrownBy(() -> TenantPrivilege.elevated(() -> {
            throw new IllegalStateException("boom");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(TenantPrivilege.isActive()).isFalse();
    }

    @Test
    void nestedScopesRestoreLayerByLayer() throws Exception {
        List<Boolean> observed = new ArrayList<>();

        TenantPrivilege.elevated((Runnable) () -> {
            observed.add(TenantPrivilege.isActive());

            // 块体无 return，确保绑定 Runnable 重载（表达式 lambda 会优先解析到 throws Exception 的 Callable 重载）
            TenantPrivilege.elevated(() -> {
                observed.add(TenantPrivilege.isActive());
            });

            observed.add(TenantPrivilege.isActive());
        });

        observed.add(TenantPrivilege.isActive());

        assertThat(observed).containsExactly(true, true, true, false);
    }

    @Test
    void elevationDoesNotLeakIntoNewThread() throws InterruptedException {
        AtomicBoolean inThread = new AtomicBoolean(false);

        Thread thread = new Thread(() -> inThread.set(TenantPrivilege.isActive()));
        thread.start();
        thread.join(5_000);

        assertThat(thread.isAlive()).isFalse();
        assertThat(inThread).isFalse();
    }

    // ===== M1 契约：elevatedInTransaction（Mockito mock 事务模板，绕开真实事务管理） =====

    /**
     * 成功路径：action 返回值正确透传；且事务 execute 回调确实在提权绑定内执行
     * （回调内 isActive()==true），出作用域后恢复非提权。
     */
    @Test
    void elevatedInTransactionShouldPassThroughResultAndBindElevationInsideCallback() throws Exception {
        TransactionTemplate transactionTemplate = mockingTransactionTemplate();

        String result = TenantPrivilege.elevatedInTransaction(transactionTemplate, () -> {
            assertThat(TenantPrivilege.isActive()).isTrue();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(TenantPrivilege.isActive()).isFalse();
    }

    /**
     * M1 红点契约：action 抛出 BusinessException（RuntimeException）时必须原样透传
     * （同类型、同消息），以便 ControllerAdvice 捕获并把业务消息返回给前端。
     * 当前实现 {@code catch (Exception e)} 把 RuntimeException 也包装为 IllegalStateException → 本用例红。
     */
    @Test
    void elevatedInTransactionShouldPassThroughBusinessExceptionUnwrapped() {
        TransactionTemplate transactionTemplate = mockingTransactionTemplate();

        assertThatThrownBy(() -> TenantPrivilege.elevatedInTransaction(transactionTemplate,
                () -> {
                    throw new BusinessException("business boom");
                }))
                .isInstanceOf(BusinessException.class)
                .hasMessage("business boom");
    }

    /**
     * 契约锁定：action 抛出受检异常时包装为 IllegalStateException 且 cause 保留原异常
     * （当前实现已满足，防回归）。
     */
    @Test
    void elevatedInTransactionShouldWrapCheckedExceptionWithCause() {
        TransactionTemplate transactionTemplate = mockingTransactionTemplate();

        assertThatThrownBy(() -> TenantPrivilege.elevatedInTransaction(transactionTemplate,
                () -> {
                    throw new Exception("boom");
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("elevated transaction failed")
                .hasRootCauseInstanceOf(Exception.class)
                .hasRootCauseMessage("boom");
    }

    /**
     * 构造 mock 事务模板：stub execute 为直接调用回调的 doInTransaction，
     * 不模拟真实事务管理（回滚/提交），保留与真实实现一致的异常传播路径。
     */
    private static TransactionTemplate mockingTransactionTemplate() {
        final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        final TransactionStatus status = new SimpleTransactionStatus();
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(status);
        });
        return transactionTemplate;
    }
}
