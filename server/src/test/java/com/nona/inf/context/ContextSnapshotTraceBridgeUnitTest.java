package com.nona.inf.context;

import com.fasterxml.jackson.databind.JsonNode;
import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.logging.IsolatedLogTestSupport;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * {@link ContextSnapshot} 跟踪身份传播面的记录级场景测试：作用域身份写入与显式清除、
 * 绑定快照的继承、嵌套作用域继承——断言锚定落盘 JSON 记录的 trace 字段（日志侧拉取模式），
 * 不断言线程日志上下文内部。
 * <p>
 * 契约验证点：
 * <ul>
 *   <li>Happy：作用域写入身份 → 记录带三字段；绑定快照携带身份 → 作用域内记录继承三字段</li>
 *   <li>Critical：嵌套作用域继承外层身份；内层写入只影响内层、退出恢复外层视角；
 *       显式清除 → 记录省略三字段；显式清除不被绑定快照复活；无可见身份 → 记录省略三字段</li>
 *   <li>边界（回归锁）：捕获面解析（持有者优先、无作用域时快照回退）、旧构造器语义与
 *       部分三元组拒绝不变</li>
 * </ul>
 * <p>
 * 记录来自每用例独立的隔离日志上下文（{@code LOG_PATH} 重定向到 {@link TempDir}），
 * 轮询等待落盘，禁止固定睡眠。
 *
 * @author nona9961
 */
@ScaffoldGenerated
class ContextSnapshotTraceBridgeUnitTest {

    /**
     * 探针 logger 名：记录归属标识。
     */
    private static final String PROBE_LOGGER = "com.nona.inf.context.ContextSnapshotTraceBridgeUnitTest";

    /**
     * 被测访问器：无参构造（纯 JUnit 形态，不经 Spring 装配）。
     */
    private final ExecutionContextAccessor accessor = new ExecutionContextAccessor();

    /**
     * 按用例隔离的日志目录。
     */
    @TempDir
    Path logDir;

    private LoggerContext loggerContext;

    // ========== fixtures ==========

    /**
     * 装载本用例专属的隔离日志上下文。
     *
     * @throws Exception 配置加载失败
     */
    @BeforeEach
    void setUp() throws Exception {
        loggerContext = IsolatedLogTestSupport.startIsolatedContext("context-snapshot-trace-bridge", logDir);
    }

    /**
     * 释放本用例专属的日志上下文；装配前置失败时上下文尚未创建，跳过。
     */
    @AfterEach
    void tearDown() {
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    // ========== Happy path ==========

    /**
     * H1（作用域写入）：作用域内写入跟踪身份 → 作用域内写日志，记录三字段取值与身份一致。
     */
    @Test
    void shouldRecordTraceFieldsFromScopeIdentity() throws Exception {
        final TraceIdentity identity = new TraceIdentity("scope-t", "scope-s", "01");
        final String marker = newMarker();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(identity);
            loggerContext.getLogger(PROBE_LOGGER).info(marker);
        });

        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsPresent(record, identity);
    }

    /**
     * H2（快照继承）：绑定携带身份的快照后嵌套作用域（异步 worker 的真实形状）→
     * 记录三字段取快照身份。
     */
    @Test
    void shouldRecordTraceFieldsInheritedFromBoundSnapshot() throws Exception {
        final TraceIdentity identity = new TraceIdentity("snapshot-t", "snapshot-s", "00");
        final ContextSnapshot snapshot = new ContextSnapshot(
                "tenant-a", List.of("admin"), "user-1", null, identity);
        final String marker = newMarker();

        ExecutionContextAccessor.withSnapshot(snapshot, () -> ExecutionContext.withScope(() ->
                loggerContext.getLogger(PROBE_LOGGER).info(marker)));

        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsPresent(record, identity);
    }

    // ========== Critical path ==========

    /**
     * C1（嵌套继承）：外层作用域写入身份、内层作用域未写入 → 内层记录继承外层三字段。
     */
    @Test
    void shouldInheritOuterIdentityIntoNestedScopeRecord() throws Exception {
        final TraceIdentity identity = new TraceIdentity("outer-t", "outer-s", "00");
        final String marker = newMarker();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(identity);
            ExecutionContext.withScope(() ->
                    loggerContext.getLogger(PROBE_LOGGER).info(marker));
        });

        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsPresent(record, identity);
    }

    /**
     * C2（显式清除）：作用域内先写入再显式清除 → 记录省略三字段。
     */
    @Test
    void shouldOmitTraceFieldsAfterExplicitClear() throws Exception {
        final String marker = newMarker();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(new TraceIdentity("cleared-t", "cleared-s", "01"));
            ExecutionContext.scope().setTraceIdentity(null);
            loggerContext.getLogger(PROBE_LOGGER).info(marker);
        });

        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsAbsent(record);
    }

    /**
     * C3（清除不被快照复活）：绑定携带身份的快照后作用域内显式清除 → 记录省略三字段
     * （作用域读取 holder-only，不回退快照）。
     */
    @Test
    void shouldNotReviveBoundSnapshotIdentityAfterExplicitClear() throws Exception {
        final ContextSnapshot snapshot = new ContextSnapshot(
                null, null, null, null, new TraceIdentity("snap-t", "snap-s", "01"));
        final String marker = newMarker();

        ExecutionContextAccessor.withSnapshot(snapshot, () -> ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(null);
            loggerContext.getLogger(PROBE_LOGGER).info(marker);
        }));

        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsAbsent(record);
    }

    /**
     * C5（内层覆盖与外层恢复）：外层身份 → 内层写入新身份并记录 → 内层记录取新值；
     * 内层退出后外层再记录仍取外层值（词法作用域栈语义）。
     */
    @Test
    void shouldScopeInnerIdentityToInnerScopeAndRestoreOuterViewAfterExit() throws Exception {
        final TraceIdentity outer = new TraceIdentity("outer-t", "outer-s", "00");
        final TraceIdentity inner = new TraceIdentity("inner-t", "inner-s", "01");
        final String innerMarker = newMarker();
        final String outerMarker = newMarker();

        ExecutionContext.withScope(() -> {
            ExecutionContext.scope().setTraceIdentity(outer);
            ExecutionContext.withScope(() -> {
                ExecutionContext.scope().setTraceIdentity(inner);
                loggerContext.getLogger(PROBE_LOGGER).info(innerMarker);
            });
            loggerContext.getLogger(PROBE_LOGGER).info(outerMarker);
        });

        IsolatedLogTestSupport.assertTraceFieldsPresent(
                IsolatedLogTestSupport.awaitRecord(logDir, innerMarker), inner);
        IsolatedLogTestSupport.assertTraceFieldsPresent(
                IsolatedLogTestSupport.awaitRecord(logDir, outerMarker), outer);
    }

    /**
     * C4（无可见身份）：作用域内未写入身份 → 记录省略三字段（不凭空注入）。
     */
    @Test
    void shouldOmitTraceFieldsWhenNoIdentityVisible() throws Exception {
        final String marker = newMarker();

        ExecutionContext.withScope(() ->
                loggerContext.getLogger(PROBE_LOGGER).info(marker));

        final JsonNode record = IsolatedLogTestSupport.awaitRecord(logDir, marker);
        IsolatedLogTestSupport.assertTraceFieldsAbsent(record);
    }

    // ========== 捕获面回归锁（既有语义不因移除线程副作用而改变） ==========

    /**
     * G1（持有者捕获）：作用域内写入跟踪身份 → captureSnapshot 整体携带该值
     * （提交线程视角，供 worker 继承）。
     */
    @Test
    void shouldCaptureTraceIdentityFromScopeHolder() {
        ExecutionContext.withScope(() -> {
            final TraceIdentity identity = new TraceIdentity("capture-t", "capture-s", "01");
            ExecutionContext.scope().setTraceIdentity(identity);

            assertThat(accessor.captureSnapshot().traceIdentity()).isEqualTo(identity);
        });
    }

    /**
     * G2（快照回退）：无作用域、仅绑定快照 → 捕获回退快照的跟踪身份
     * （整体回退，不做字段级拼接）。
     */
    @Test
    void shouldFallBackToBoundSnapshotTraceIdentityWhenNoScopeBound() {
        final TraceIdentity boundIdentity = new TraceIdentity("bound-t", "bound-s", "00");
        final ContextSnapshot bound = new ContextSnapshot(
                "tenant-a", List.of("admin"), "user-1", null, boundIdentity);

        ExecutionContextAccessor.withSnapshot(bound, () ->
                assertThat(accessor.captureSnapshot().traceIdentity()).isEqualTo(boundIdentity));
    }

    /**
     * G3（持有者优先）：持有者与已绑定快照同时携带跟踪身份时，捕获取持有者值
     * （与三元组解析同序：holder 优先）。
     */
    @Test
    void shouldPreferHolderTraceIdentityOverBoundSnapshot() {
        final ContextSnapshot bound = new ContextSnapshot(
                "tenant-a", List.of("admin"), "user-1", null,
                new TraceIdentity("bound-t", "bound-s", "00"));

        ExecutionContextAccessor.withSnapshot(bound, () -> ExecutionContext.withScope(() -> {
            final TraceIdentity holderIdentity = new TraceIdentity("holder-t", "holder-s", "01");
            ExecutionContext.scope().setTraceIdentity(holderIdentity);

            assertThat(accessor.captureSnapshot().traceIdentity()).isEqualTo(holderIdentity);
        }));
    }

    /**
     * G4（构造器兼容与空态）：既有三元组构造器（3 参 / 4 参）与 EMPTY 语义不变——
     * 跟踪身份缺省为 {@code null}；无任何绑定来源时捕获为空。
     */
    @Test
    void shouldKeepLegacySnapshotConstructorsTraceIdentityFree() {
        final ContextSnapshot threeArg = new ContextSnapshot("tenant-a", List.of("admin"), "user-1");
        final ContextSnapshot fourArg =
                new ContextSnapshot("tenant-a", List.of("admin"), "user-1", null);

        assertThat(threeArg.traceIdentity()).isNull();
        assertThat(fourArg.traceIdentity()).isNull();
        assertThat(ContextSnapshot.EMPTY.traceIdentity()).isNull();
        assertThat(accessor.captureSnapshot().traceIdentity()).isNull();
    }

    /**
     * G5（部分三元组拒绝）：任一字段为 null 的构造被拒绝——跟踪身份要么整体存在、
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

    // ========== helpers ==========

    /**
     * 生成用例唯一探针标记。
     *
     * @return 探针标记
     */
    private static String newMarker() {
        return "trace-probe-" + UUID.randomUUID();
    }
}
