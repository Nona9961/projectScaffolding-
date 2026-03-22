package com.nona.inf.context;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.ScopeNotActiveException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * 租户上下文读取器（基于 {@link ThreadContext}）。
 *
 * @author nona
 */
@Component
@RequiredArgsConstructor
public class TenantContextAccessor {

    /**
     * tenant 缺失时使用的占位值，用于 fail-closed（不放行 tenant-scoped 数据）。
     */
    public static final String MISSING_TENANT_ID = "__MISSING_TENANT__";

    private static final String CROSS_TENANT_KEY = "CROSS_TENANT";

    private final ObjectProvider<ThreadContext> threadContextProvider;

    /**
     * 获取当前请求上下文中的 tenantID。
     *
     * @return tenantID；若当前未处于 {@link ThreadContext} 的 request scope，或 tenantID 缺失/为空白则返回 {@code null}
     */
    @Nullable
    public String getTenantID() {
        ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext == null) {
            return null;
        }

        String tenantID = threadContext.getTenantID();
        if (tenantID == null || tenantID.isBlank()) {
            return null;
        }
        return tenantID;
    }

    /**
     * 获取当前请求上下文中的 tenantID；tenant 缺失时返回占位值。
     *
     * @return tenantID；若 tenant 缺失则返回 {@link #MISSING_TENANT_ID}
     */
    public String getTenantIDOrMissing() {
        String tenantID = getTenantID();
        if (tenantID == null) {
            return MISSING_TENANT_ID;
        }
        return tenantID;
    }

    /**
     * 判断当前调用链是否显式开启了跨租户模式。
     *
     * @return 是否启用跨租户模式
     */
    public boolean isCrossTenant() {
        ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext == null) {
            return false;
        }

        Boolean value = threadContext.getAttribute(CROSS_TENANT_KEY);
        return Boolean.TRUE.equals(value);
    }

    /**
     * 设置当前调用链的跨租户开关（作用域由调用方控制）。
     *
     * @param enabled 是否启用跨租户模式
     * @throws IllegalStateException 当 {@link ThreadContext} 的 request scope 未激活时抛出
     */
    public void setCrossTenant(boolean enabled) {
        ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext == null) {
            throw new IllegalStateException("ThreadContext scope is not active");
        }

        if (enabled) {
            threadContext.setAttribute(CROSS_TENANT_KEY, true);
            return;
        }
        threadContext.removeAttribute(CROSS_TENANT_KEY);
    }

    /**
     * 在 request scope 激活时获取 {@link ThreadContext}；否则返回 {@code null}。
     *
     * @return 当前 ThreadContext；若 request scope 未激活则返回 {@code null}
     */
    @Nullable
    private ThreadContext getThreadContextIfActive() {
        try {
            return threadContextProvider.getObject();
        } catch (ScopeNotActiveException ex) {
            return null;
        }
    }
}