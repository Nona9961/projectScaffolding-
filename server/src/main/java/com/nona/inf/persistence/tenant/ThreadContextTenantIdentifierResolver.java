package com.nona.inf.persistence.tenant;

import com.nona.inf.context.TenantContextAccessor;
import lombok.RequiredArgsConstructor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;
import com.nona.annotation.ScaffoldGenerated;

/**
 * Hibernate tenant identifier resolver（discriminator multi-tenancy）
 * <p>
 * - 默认：使用当前 ThreadContext 的 tenantID
 * - tenant 缺失：返回 {@link TenantContextAccessor#MISSING_TENANT_ID} 实现 fail-closed
 * - cross-tenant：返回 root tenant，绕过 Hibernate 内置的 _tenantId filter
 *
 * @author nona
 */
@Component
@RequiredArgsConstructor
@ScaffoldGenerated
public class ThreadContextTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    /**
     * 根租户 ID：cross-tenant 模式下返回该值以绕过 discriminator 过滤
     */
    public static final String ROOT_TENANT_ID = "__ROOT_TENANT__";

    /**
     * 租户上下文访问器（两级优先级：请求作用域 → 线程回退）
     */
    private final TenantContextAccessor tenantContextAccessor;

    /**
     * 为 Hibernate 解析当前会话的 tenant identifier。
     *
     * @return 当前 tenant identifier；cross-tenant 时返回 {@link #ROOT_TENANT_ID}，tenant 缺失时返回 {@link TenantContextAccessor#MISSING_TENANT_ID}
     */
    @Override
    public String resolveCurrentTenantIdentifier() {
        if (tenantContextAccessor.isCrossTenant()) {
            return ROOT_TENANT_ID;
        }
        return tenantContextAccessor.getTenantIDOrMissing();
    }

    /**
     * 是否校验已存在的 Session。
     *
     * @return 固定返回 false（不做校验）
     */
    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    /**
     * 判断指定 tenant 是否为 root tenant（用于绕过 discriminator 过滤）。
     *
     * @param tenantId tenant identifier
     * @return 是否为 root tenant
     */
    @Override
    public boolean isRoot(String tenantId) {
        return ROOT_TENANT_ID.equals(tenantId);
    }
}