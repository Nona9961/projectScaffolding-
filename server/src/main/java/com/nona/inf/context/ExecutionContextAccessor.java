package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.changeTracking.domain.model.tracking.BaselineSnapshot;
import com.nona.tenant.TenantWriteGate;
import jakarta.annotation.Nullable;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 租户上下文读取器（单级解析：基于 {@link ExecutionContext} 的 ScopedValue 主通道）。
 * <p>
 * 解析顺序：
 * <ol>
 *   <li>{@link ExecutionContext#scope()} 持有者（holder）优先——字段非空/非空白才取；
 *       holder 由入口组件（{@link ExecutionContextFilter} / 任务传播装饰器）的 {@code withScope}
 *       建立，授权过滤器在作用域内写入三元组</li>
 *   <li>{@link #boundSnapshot()} 嵌套异步回退——worker 线程（经 {@link #withSnapshot}
 *       绑定）丢失 holder 字段时继承外层快照视角</li>
 *   <li>两者皆无可取 → {@code null}（fail-closed：消费方落
 *       {@link #MISSING_TENANT_ID} 占位，不放行 tenant-scoped 数据）</li>
 * </ol>
 * 请求作用域 bean 不再参与读取。
 * <p>
 * 异步线程回退存储基于 {@link ScopedValue}（与 {@link TenantPrivilege} 同载体）：绑定
 * 仅存在于 {@link #withSnapshot} 作用域内，出作用域（含异常路径）自动恢复 unbound——
 * 无需手动清理，池化线程复用无残留（JEP 506 语义，虚拟线程友好）。
 *
 * @author nona9961
 */
@Component
@ScaffoldGenerated
public class ExecutionContextAccessor {

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
     * 在指定快照的绑定作用域内执行 {@code action}；作用域退出（含异常路径）自动恢复 unbound。
     * <p>
     * 跨线程传播的统一入口：提交线程经 {@link #captureSnapshot()} 捕获，worker 线程以本方法
     * 包裹任务体。快照携带的跟踪身份在其后嵌套的 {@link ExecutionContext#withScope(Runnable)}
     * 中被继承为作用域可见身份（异步 worker 保持提交线程视角）；本方法自身只绑定快照。
     * 不调用本方法时线程即无回退（fail-closed）。
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
     * 包可见：供同包 {@link ExecutionContextState} 首次创建钩子（读取 SNAPSHOT 槽的
     * {@code trackingBaseline}）与 {@link #visibleTraceIdentity()} 消费；
     * 仅限包内消费，不对外暴露。
     *
     * @return 绑定中的快照；未绑定返回 {@code null}
     */
    @Nullable
    static ContextSnapshot boundSnapshot() {
        return SNAPSHOT.isBound() ? SNAPSHOT.get() : null;
    }

    /**
     * 解析当前线程的可见跟踪身份：作用域持有者身份整体优先，否则回退已绑定快照的
     * 跟踪身份；两源皆无 → {@code null}。
     * <p>
     * 包可见：供同包 {@link ExecutionContext#withScope(Runnable)}（进入新作用域时继承）与
     * {@link #captureSnapshot()}（跨线程捕获）共用同一解析，避免双实现漂移。
     *
     * @return 可见跟踪身份；无从可取得时为 {@code null}
     */
    @Nullable
    static TraceIdentity visibleTraceIdentity() {
        final ExecutionContextState scope = ExecutionContext.scope();
        if (scope != null && scope.getTraceIdentity() != null) {
            return scope.getTraceIdentity();
        }
        final ContextSnapshot bound = boundSnapshot();
        return bound != null ? bound.traceIdentity() : null;
    }

    /**
     * 字段级解析：holder 非空且非空白才取；否则回退 boundSnapshot；两源皆无 → {@code null}。
     */
    @Nullable
    private static String tenantIdOrNull(@Nullable ExecutionContextState scope, @Nullable ContextSnapshot bound) {
        if (scope != null) {
            final String tenantID = scope.getTenantID();
            if (tenantID != null && !tenantID.isBlank()) {
                return tenantID;
            }
        }
        if (bound != null) {
            final String tenantID = bound.tenantID();
            if (tenantID != null && !tenantID.isBlank()) {
                return tenantID;
            }
        }
        return null;
    }

    /**
     * 字段级解析：holder 非空才取；否则回退 boundSnapshot；两源皆无 → {@code null}。
     */
    @Nullable
    private static List<String> roleOrNull(@Nullable ExecutionContextState scope, @Nullable ContextSnapshot bound) {
        if (scope != null && scope.getRole() != null) {
            return scope.getRole();
        }
        if (bound != null) {
            return bound.role();
        }
        return null;
    }

    /**
     * 字段级解析：holder 非空才取；否则回退 boundSnapshot；两源皆无 → {@code null}。
     */
    @Nullable
    private static String identityOrNull(@Nullable ExecutionContextState scope, @Nullable ContextSnapshot bound) {
        if (scope != null && scope.getIdentity() != null) {
            return scope.getIdentity();
        }
        if (bound != null) {
            return bound.identity();
        }
        return null;
    }

    /**
     * 捕获当前上下文身份三元组（tenantID / role / identity）、追踪基线与跟踪身份，供跨线程传播。
     * <p>
     * 解析顺序：{@link ExecutionContext#scope()} 持有者优先——无作用域时读当前线程已绑定快照
     * （嵌套异步：worker 内再派发继承外层视角）——两者皆无返回三元组全空快照。
     * <p>
     * <strong>追踪基线</strong>：基线捕获与三元组解析相互独立——
     * 仅当当前作用域（{@link ExecutionContext#scope()}）已存在追踪器时，经
     * {@code captureBaseline()} 导出深拷贝基线（不得触发创建：非 DB 请求零追踪开销的
     * 懒语义不被捕获动作破坏）；无作用域或尚无追踪器时基线为 {@code null}（合法态，
     * worker 侧走普通创建路径）。
     * <p>
     * <strong>跟踪身份</strong>：整体捕获（三分量原子，无字段级回退）——与
     * {@link ExecutionContext#withScope(Runnable)} 的继承解析共用
     * {@link #visibleTraceIdentity()}：作用域持有者非空取持有者，否则回退已绑定快照的
     * 跟踪身份；两源皆无 → {@code null}（合法态：无跟踪身份）。
     *
     * @return 当前上下文快照（三元组 + 可能存在的追踪基线与跟踪身份）；三元组按字段级解析：
     *         holder 有值取 holder，否则回退 boundSnapshot，两源皆无 → {@code null}
     *         （无作用域且无追踪器时返回全空快照）
     */
    public ContextSnapshot captureSnapshot() {
        final ExecutionContextState scope = ExecutionContext.scope();
        final BaselineSnapshot trackingBaseline =
                scope != null && scope.trackerIfPresent() != null
                        ? scope.trackerIfPresent().captureBaseline()
                        : null;

        final ContextSnapshot bound = boundSnapshot();
        return new ContextSnapshot(
                tenantIdOrNull(scope, bound),
                roleOrNull(scope, bound),
                identityOrNull(scope, bound),
                trackingBaseline,
                visibleTraceIdentity()
        );
    }

    /**
     * 获取当前上下文中的 tenantID。
     * <p>
     * 解析顺序（单级）：
     * <ol>
     *   <li>{@link ExecutionContext#scope()} 持有者（holder）——字段非空且非空白才取
     *       （空白视为缺失，继续回退）</li>
     *   <li>ScopedValue 回退（经 {@link #withSnapshot(ContextSnapshot, Runnable)} 绑定）——
     *       嵌套异步 worker 继承外层视角；作用域退出自动恢复</li>
     *   <li>两者皆无可取 → {@code null}（fail-closed）</li>
     * </ol>
     *
     * @return tenantID；若当前无作用域/已绑定快照，或字段缺失/为空白则返回 {@code null}
     */
    @Nullable
    public String getTenantID() {
        return tenantIdOrNull(ExecutionContext.scope(), boundSnapshot());
    }

    /**
     * 获取当前上下文中的 tenantID；tenant 缺失时返回占位值。
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
     * 获取当前上下文中的 role 列表。
     * <p>
     * 解析顺序（单级）：1) {@link ExecutionContext#scope()} 持有者（字段非空才取）→
     * 2) {@link #boundSnapshot()} 嵌套回退 → 3) {@code null}。
     *
     * @return role 列表；若缺失则返回 {@code null}
     */
    @Nullable
    public List<String> getRole() {
        return roleOrNull(ExecutionContext.scope(), boundSnapshot());
    }

    /**
     * 获取当前上下文中的 identity。
     * <p>
     * 解析顺序（单级）：1) {@link ExecutionContext#scope()} 持有者（字段非空才取）→
     * 2) {@link #boundSnapshot()} 嵌套回退 → 3) {@code null}。
     *
     * @return identity；若缺失则返回 {@code null}
     */
    @Nullable
    public String getIdentity() {
        return identityOrNull(ExecutionContext.scope(), boundSnapshot());
    }

}
