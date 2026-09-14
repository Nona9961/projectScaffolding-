package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.changeTracking.domain.model.tracking.ChangeTracker;
import com.nona.inf.persistence.tracking.ChangeTrackerProvider;
import jakarta.annotation.Nullable;

import java.util.Objects;

/**
 * 执行上下文静态门面：一次执行（HTTP 请求 / 异步任务 / 消息消费 / 定时任务）的上下文
 * 统一到单一 {@link ScopedValue} 通道（{@code ExecutionContextState} 持有者载体）。
 * <p>
 * 语义要点：
 * <ul>
 *   <li><b>词法作用域</b>：{@link #withScope(Runnable)} 绑定新的持有者并继承当前可见跟踪身份；
 *       作用域退出（含异常路径）自动恢复 unbound，无需手动清理，池化线程复用无残留（JEP 506 语义）</li>
 *   <li><b>懒创建</b>：入口绑定新持有者（引用级开销）；首次 {@link #tracker(ChangeTrackerProvider)}
 *       调用才经提供者创建追踪器并留存——非 DB 访问路径零追踪开销</li>
 *   <li><b>fail-closed</b>：未绑定作用域时调用 {@link #tracker(ChangeTrackerProvider)}
 *       抛出 {@link IllegalStateException}（提示缺失入口组件注册），不静默降级</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
public final class ExecutionContext {

    /**
     * 单一 ScopedValue 通道：值为每作用域的 {@link ExecutionContextState} 持有者。
     * 仅经 {@link #withScope(Runnable)} 绑定，出作用域自动恢复 unbound。
     */
    private static final ScopedValue<ExecutionContextState> STATE = ScopedValue.newInstance();

    private ExecutionContext() {
    }

    /**
     * 在新建的执行作用域内执行 {@code action}：绑定一个新的 {@link ExecutionContextState} 持有者，
     * 退出（含异常路径）自动恢复 unbound。
     * <p>
     * 新持有者继承「当前可见跟踪身份」：外层持有者身份优先，无持有者时回退已绑定快照的
     * 跟踪身份——未写入身份的内层作用域与异步 worker 保持外层视角。
     * <p>
     * 所有请求 / 任务入口（HTTP 过滤器、异步任务装饰器等）必须以本方法包裹任务体；
     * 不包裹时线程即为未绑定（fail-closed）。
     *
     * @param action 绑定作用域内执行的操作
     */
    public static void withScope(Runnable action) {
        final ExecutionContextState state = new ExecutionContextState();
        state.setTraceIdentity(ExecutionContextAccessor.visibleTraceIdentity());
        ScopedValue.where(STATE, state).run(action);
    }

    /**
     * 获取当前作用域的 {@link ExecutionContextState} 持有者（写 / 读访问入口）。
     *
     * @return 当前作用域的持有者；未绑定作用域时返回 {@code null}
     */
    @Nullable
    public static ExecutionContextState scope() {
        return STATE.isBound() ? STATE.get() : null;
    }

    /**
     * 读取当前作用域的跟踪身份（holder-only，不做快照回退）。
     * <p>
     * 供日志侧消费者在事件创建时拉取；作用域内显式清除（{@code setTraceIdentity(null)}）后
     * 即无身份，不得被跨线程快照复活。
     *
     * @return 当前作用域的跟踪身份；未绑定作用域或该作用域无身份时为 {@code null}
     */
    @Nullable
    public static TraceIdentity currentTraceIdentity() {
        final ExecutionContextState current = scope();
        return current != null ? current.getTraceIdentity() : null;
    }

    /**
     * 获取当前作用域的变更追踪器（懒创建）。
     * <p>
     * 绑定作用域内：首次调用经 {@code provider.create()} 创建并留存于当前持有者，
     * 后续调用返回同一实例；未绑定作用域时抛 {@link IllegalStateException}（fail-closed，
     * 提示注册入口组件）。
     *
     * @param provider ChangeTracker 创建提供者；不得为 {@code null}
     * @return 当前作用域的 ChangeTracker 实例（懒创建）
     * @throws IllegalStateException 未绑定作用域（入口组件缺失）时抛出
     */
    public static ChangeTracker tracker(ChangeTrackerProvider provider) {
        Objects.requireNonNull(provider, "provider cannot be null");
        final ExecutionContextState current = scope();
        if (current == null) {
            throw new IllegalStateException(
                    "未绑定执行作用域：请经入口组件（ExecutionContextFilter / 任务传播装饰器）以 ExecutionContext.withScope 包裹任务体后调用");
        }
        return current.getOrCreateTracker(provider);
    }
}