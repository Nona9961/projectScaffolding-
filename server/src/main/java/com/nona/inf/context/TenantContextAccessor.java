package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.changeTracking.domain.model.tracking.BaselineSnapshot;
import com.nona.tenant.TenantWriteGate;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;

/**
 * 租户上下文读取器（基于 {@link ThreadContext}）。
 * <p>
 * 异步线程回退存储基于 {@link ScopedValue}（与 {@link TenantPrivilege} 同载体）：绑定
 * 仅存在于 {@link #withSnapshot} 作用域内，出作用域（含异常路径）自动恢复 unbound——
 * 无需手动清理，池化线程复用无残留（JEP 506 语义，虚拟线程友好）。
 *
 * @author nona9961
 */
@Component
@RequiredArgsConstructor
@ScaffoldGenerated
public class TenantContextAccessor {

    /**
     * tenant 缺失时使用的占位值，用于 fail-closed（不放行 tenant-scoped 数据）。
     * <p>
     * 转发常量：权威定义在 common 的 {@link TenantWriteGate#MISSING_TENANT_ID}（规则与常量统一上移 common，此处转发以兼容既有调用）。
     * 保留本转发位以兼容现有引用（getTenantIDOrMissing 与测试）。
     */
    public static final String MISSING_TENANT_ID = TenantWriteGate.MISSING_TENANT_ID;

    /**
     * 异步线程回退存储：worker 线程经 {@link #withSnapshot} 绑定；
     * 仅作用域内可读，出作用域自动恢复 unbound。
     */
    private static final ScopedValue<ContextSnapshot> SNAPSHOT = ScopedValue.newInstance();

    /**
     * 请求作用域 ThreadContext 的懒加载提供者
     */
    private final ObjectProvider<ThreadContext> threadContextProvider;

    /**
     * 在指定快照的绑定作用域内执行 {@code action}；作用域退出（含异常路径）自动恢复 unbound。
     * <p>
     * 跨线程传播的统一入口：提交线程经 {@link #captureSnapshot()} 捕获，worker 线程以本方法
     * 包裹任务体。不调用本方法时线程即无回退（fail-closed）。
     *
     * @param snapshot 要绑定的快照；不得为 {@code null}（无需传播时不要调用本方法）
     * @param action   绑定作用域内执行的操作
     */
    public static void withSnapshot(ContextSnapshot snapshot, Runnable action) {
        ScopedValue.where(SNAPSHOT, snapshot).run(action);
    }

    /**
     * 读取当前线程已绑定的快照；未绑定时返回 {@code null}。
     * <p>
     * 包可见：供同包 {@link TrackingScope} 首次创建钩子读取
     * SNAPSHOT 槽的 {@code trackingBaseline}（异步 worker 基线重建判定）；
     * 仅限包内消费，不对外暴露。
     *
     * @return 绑定中的快照；未绑定返回 {@code null}
     */
    @Nullable
    static ContextSnapshot boundSnapshot() {
        return SNAPSHOT.isBound() ? SNAPSHOT.get() : null;
    }

    /**
     * 捕获当前上下文身份三元组（tenantID / role / identity）与追踪基线，供跨线程传播。
     * <p>
     * 解析顺序：请求作用域的 {@link ThreadContext} 优先——无请求作用域时读当前线程已绑定快照
     * （嵌套异步：worker 内再派发继承外层视角）——两者皆无返回 {@link ContextSnapshot#EMPTY}。
     * <p>
     * <strong>追踪基线</strong>：基线捕获与三元组解析相互独立——
     * 仅当当前作用域（{@link TrackingContext#scope()}）已存在追踪器时，经
     * {@code captureBaseline()} 导出深拷贝基线（不得触发创建：非 DB 请求零追踪开销的
     * 懒语义不被捕获动作破坏）；无作用域或尚无追踪器时基线为 {@code null}（合法态，
     * worker 侧走普通创建路径）。
     *
     * @return 当前上下文快照（三元组 + 可能存在的追踪基线）；请求作用域与已绑定快照
     *         皆缺失且无追踪器时返回全空快照
     */
    public ContextSnapshot captureSnapshot() {
        final TrackingScope scope = TrackingContext.scope();
        final BaselineSnapshot trackingBaseline =
                scope != null && scope.trackerIfPresent() != null
                        ? scope.trackerIfPresent().captureBaseline()
                        : null;

        final ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext != null) {
            return new ContextSnapshot(
                    threadContext.getTenantID(),
                    threadContext.getRole(),
                    threadContext.getIdentity(),
                    trackingBaseline
            );
        }
        final ContextSnapshot bound = boundSnapshot();
        if (bound != null) {
            return new ContextSnapshot(
                    bound.tenantID(),
                    bound.role(),
                    bound.identity(),
                    trackingBaseline
            );
        }
        return new ContextSnapshot(null, null, null, trackingBaseline);
    }

    /**
     * 获取当前请求上下文中的 tenantID。
     * <p>
     * 解析顺序：
     * <ol>
     *   <li>{@link ThreadContext}（请求作用域 bean）—— 请求作用域激活时</li>
     *   <li>ScopedValue 回退（经 {@link #withSnapshot(ContextSnapshot, Runnable)} 绑定）——
     *       异步 worker 线程；作用域退出自动恢复</li>
     * </ol>
     *
     * @return tenantID；若当前未处于 {@link ThreadContext} 的 request scope，或 tenantID 缺失/为空白则返回 {@code null}
     */
    @Nullable
    public String getTenantID() {
        final ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext != null) {
            final String tenantID = threadContext.getTenantID();
            if (tenantID != null && !tenantID.isBlank()) {
                return tenantID;
            }
        }

        final ContextSnapshot snapshot = boundSnapshot();
        if (snapshot != null) {
            final String tenantID = snapshot.tenantID();
            if (tenantID != null && !tenantID.isBlank()) {
                return tenantID;
            }
        }
        return null;
    }

    /**
     * 获取当前请求上下文中的 tenantID；tenant 缺失时返回占位值。
     *
     * @return tenantID；若 tenant 缺失则返回 {@link #MISSING_TENANT_ID}
     */
    public String getTenantIDOrMissing() {
        final String tenantID = getTenantID();
        if (tenantID == null) {
            return MISSING_TENANT_ID;
        }
        return tenantID;
    }

    /**
     * 获取当前请求上下文中的 role 列表。
     * <p>
     * 解析顺序：1) 请求作用域 {@link ThreadContext} → 2) ScopedValue 回退。
     *
     * @return role 列表；若缺失则返回 {@code null}
     */
    @Nullable
    public List<String> getRole() {
        final ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext != null) {
            final List<String> role = threadContext.getRole();
            if (role != null) {
                return role;
            }
        }

        final ContextSnapshot snapshot = boundSnapshot();
        if (snapshot != null) {
            return snapshot.role();
        }
        return null;
    }

    /**
     * 获取当前请求上下文中的 identity。
     * <p>
     * 解析顺序：1) 请求作用域 {@link ThreadContext} → 2) ScopedValue 回退。
     *
     * @return identity；若缺失则返回 {@code null}
     */
    @Nullable
    public String getIdentity() {
        final ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext != null) {
            final String identity = threadContext.getIdentity();
            if (identity != null) {
                return identity;
            }
        }

        final ContextSnapshot snapshot = boundSnapshot();
        if (snapshot != null) {
            return snapshot.identity();
        }
        return null;
    }

    /**
     * 在 request scope 激活时获取 {@link ThreadContext}；否则返回 {@code null}。
     *
     * @return 当前 ThreadContext；若 request scope 未激活则返回 {@code null}
     */
    @Nullable
    ThreadContext getThreadContextIfActive() {
        if (RequestContextHolder.getRequestAttributes() == null) {
            return null;
        }
        return threadContextProvider.getIfAvailable();
    }

    /**
     * 跨切面上下文快照（不可变 record），供异步线程传播。
     * <p>
     * 承载 {@link ThreadContext} 中安全且异步必需的三元组（tenantID / role / identity）
     * 与追踪基线（{@code trackingBaseline}——{@code tracker.captureBaseline()} 的深拷贝产出；
     * 提交线程无追踪器时为 {@code null}）。attributes 与快照被有意排除——
     * 它们可能携带请求作用域可变状态。
     *
     * @param tenantID        租户 ID；可能为 null
     * @param role            角色列表；可能为 null
     * @param identity        请求者身份；可能为 null
     * @param trackingBaseline 追踪基线（深拷贝）；无追踪器时为 null
     */
    public record ContextSnapshot(
            @Nullable String tenantID,
            @Nullable List<String> role,
            @Nullable String identity,
            @Nullable BaselineSnapshot trackingBaseline
    ) {
        /** 表示缺失 / 已清除上下文的哨兵快照（三元组与追踪基线均为空）。 */
        public static final ContextSnapshot EMPTY = new ContextSnapshot(null, null, null, null);

        /**
         * 兼容便捷构造器：仅三元组（追踪基线缺省为 {@code null}）。
         * <p>
         * 保留以兼容既有调用形态（传播槽结构不变）；基线缺省语义 =
         * 「无追踪器 / 不传播基线」。
         *
         * @param tenantID 租户 ID；可能为 null
         * @param role     角色列表；可能为 null
         * @param identity 请求者身份；可能为 null
         */
        public ContextSnapshot(
                @Nullable String tenantID,
                @Nullable List<String> role,
                @Nullable String identity) {
            this(tenantID, role, identity, null);
        }
    }
}
