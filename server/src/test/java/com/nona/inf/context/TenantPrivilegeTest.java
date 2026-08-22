package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link TenantPrivilege} 单测：纯 JUnit5 + AssertJ，不依赖 Spring。
 *
 * @author nona
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

            // 内层已退出，外层作用域仍然生效
            observed.add(TenantPrivilege.isActive());
        });

        // 最外层退出后恢复
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
}
