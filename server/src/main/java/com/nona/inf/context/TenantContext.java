package com.nona.inf.context;

import java.util.function.Supplier;

/**
 * Tenant 上下文（ThreadLocal）
 * <p>
 * Repository 层从这里读取 tenantID 来实现自动注入过滤（见 ADR-001）。
 *
 * @author nona
 */
public final class TenantContext {

    /**
     * 当 tenant 缺失时使用的占位值，用于 fail-closed（不放行 tenant-scoped 数据）。
     */
    public static final String MISSING_TENANT_ID = "__MISSING_TENANT__";

    private static final ThreadLocal<String> TENANT_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> CROSS_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantID(String tenantID) {
        TENANT_ID.set(tenantID);
    }

    public static String getTenantID() {
        return TENANT_ID.get();
    }

    public static boolean isCrossTenant() {
        return Boolean.TRUE.equals(CROSS_TENANT.get());
    }

    public static void setCrossTenant(boolean enabled) {
        if (enabled) {
            CROSS_TENANT.set(true);
            return;
        }
        CROSS_TENANT.remove();
    }

    public static <T> T withCrossTenant(Supplier<T> supplier) {
        final boolean previous = isCrossTenant();
        setCrossTenant(true);
        try {
            return supplier.get();
        } finally {
            setCrossTenant(previous);
        }
    }

    public static void clear() {
        TENANT_ID.remove();
        CROSS_TENANT.remove();
    }
}
