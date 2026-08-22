package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;

/**
 * 提权作用域生命周期监听器：持久化适配层通过该钩子在提权进入/退出时切换读路径过滤状态。
 * <p>
 * 时序契约（由 {@link TenantPrivilege} 保证）：
 * <ul>
 *   <li>{@link #onElevatedEnter()} 在 ScopedValue 绑定前调用；{@link #onElevatedExit()} 在 ScopedValue
 *       解绑后调用（本层已解绑，但外层提权可能仍绑定）</li>
 *   <li>{@code onElevatedExit()} 执行时若仍有外层提权绑定（{@link TenantPrivilege#isActive()} 为
 *       {@code true}），实现方不得恢复任何状态</li>
 *   <li>嵌套场景会连续调用：{@code onEnter/onExit} 必须可重入安全</li>
 * </ul>
 *
 * @author nona9961
 */
@ScaffoldGenerated
public interface TenantScopeListener {

    /**
     * 提权作用域进入回调（ScopedValue 绑定前）。
     */
    void onElevatedEnter();

    /**
     * 提权作用域退出回调（ScopedValue 解绑后，本层已解绑）。
     */
    void onElevatedExit();
}
