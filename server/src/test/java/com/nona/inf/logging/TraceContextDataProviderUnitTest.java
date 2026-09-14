package com.nona.inf.logging;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.context.ContextSnapshot;
import com.nona.inf.context.ExecutionContext;
import com.nona.inf.context.ExecutionContextAccessor;
import com.nona.inf.context.TraceIdentity;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TraceContextDataProvider} 场景测试：日志事件创建时从执行上下文拉取跟踪身份——
 * 有身份映射为三键、无身份返回空映射（不得为 {@code null}：多提供者合并路径直接
 * {@code putAll}）。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：作用域写入身份 → 三键映射取值一致</li>
 *   <li>Critical：绑定快照携带身份 → 作用域继承后三键映射；无作用域 / 作用域无身份 /
 *       显式清除 → 空映射；显式清除不被快照复活</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
class TraceContextDataProviderUnitTest {

    /**
     * 被测提供者：无依赖直构（纯 JUnit 形态，不经容器）。
     */
    private final TraceContextDataProvider provider = new TraceContextDataProvider();

    // ========== Happy path ==========

    /**
     * H1（作用域身份）：作用域内写入跟踪身份 → 三键映射与身份一致。
     */
    @Test
    void shouldMapScopeIdentityToThreeKeys() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(new TraceIdentity("trace-1", "span-1", "01"));

            assertThat(provider.supplyContextData()).containsExactlyInAnyOrderEntriesOf(Map.of(
                    TraceContextDataProvider.TRACE_ID_KEY, "trace-1",
                    TraceContextDataProvider.SPAN_ID_KEY, "span-1",
                    TraceContextDataProvider.TRACE_FLAGS_KEY, "01"));
        });
    }

    /**
     * H2（快照继承）：绑定携带身份的快照后嵌套作用域（异步 worker 的真实形状）→
     * 作用域继承身份，三键映射取快照值。
     */
    @Test
    void shouldMapSnapshotIdentityInheritedByScope() {
        final TraceIdentity identity = new TraceIdentity("snap-t", "snap-s", "00");
        final ContextSnapshot snapshot = new ContextSnapshot(null, null, null, null, identity);

        ExecutionContextAccessor.withSnapshot(snapshot, () -> ExecutionContext.withScope(() ->
                assertThat(provider.supplyContextData()).containsExactlyInAnyOrderEntriesOf(Map.of(
                        TraceContextDataProvider.TRACE_ID_KEY, "snap-t",
                        TraceContextDataProvider.SPAN_ID_KEY, "snap-s",
                        TraceContextDataProvider.TRACE_FLAGS_KEY, "00"))));
    }

    // ========== Critical path ==========

    /**
     * C1（无作用域）：未绑定执行作用域 → 空映射（非 {@code null}，合并路径安全）。
     */
    @Test
    void shouldReturnEmptyMapWithoutBoundScope() {
        assertThat(provider.supplyContextData()).isNotNull().isEmpty();
    }

    /**
     * C2（作用域无身份）：作用域内未写入跟踪身份 → 空映射（不凭空注入）。
     */
    @Test
    void shouldReturnEmptyMapWhenScopeHasNoIdentity() {
        ExecutionContext.withScope(() ->
                assertThat(provider.supplyContextData()).isNotNull().isEmpty());
    }

    /**
     * C3（显式清除）：作用域内写入后显式清除 → 空映射。
     */
    @Test
    void shouldReturnEmptyMapAfterIdentityClearedExplicitly() {
        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(new TraceIdentity("trace-3", "span-3", "01"));
            ExecutionContext.scope().setTraceIdentity(null);

            assertThat(provider.supplyContextData()).isNotNull().isEmpty();
        });
    }

    /**
     * C4（清除不被快照复活）：绑定携带身份的快照后作用域内显式清除 → 空映射
     * （作用域读取 holder-only，不回退快照）。
     */
    @Test
    void shouldReturnEmptyMapAfterExplicitClearDespiteBoundSnapshot() {
        final ContextSnapshot snapshot = new ContextSnapshot(
                null, null, null, null, new TraceIdentity("snap-t", "snap-s", "01"));

        ExecutionContextAccessor.withSnapshot(snapshot, () -> ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(null);

            assertThat(provider.supplyContextData()).isNotNull().isEmpty();
        }));
    }
}
