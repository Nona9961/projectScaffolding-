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

    public String getTenantIDOrMissing() {
        String tenantID = getTenantID();
        if (tenantID == null) {
            return MISSING_TENANT_ID;
        }
        return tenantID;
    }

    public boolean isCrossTenant() {
        ThreadContext threadContext = getThreadContextIfActive();
        if (threadContext == null) {
            return false;
        }

        Boolean value = threadContext.getAttribute(CROSS_TENANT_KEY);
        return Boolean.TRUE.equals(value);
    }

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

    @Nullable
    private ThreadContext getThreadContextIfActive() {
        try {
            return threadContextProvider.getObject();
        } catch (ScopeNotActiveException ex) {
            return null;
        }
    }
}

