package com.nona.inf.persistence.tenant;

import com.nona.inf.context.TenantContextAccessor;
import lombok.RequiredArgsConstructor;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

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
public class ThreadContextTenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    public static final String ROOT_TENANT_ID = "__ROOT_TENANT__";

    private final TenantContextAccessor tenantContextAccessor;

    @Override
    public String resolveCurrentTenantIdentifier() {
        if (tenantContextAccessor.isCrossTenant()) {
            return ROOT_TENANT_ID;
        }
        return tenantContextAccessor.getTenantIDOrMissing();
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return false;
    }

    @Override
    public boolean isRoot(String tenantId) {
        return ROOT_TENANT_ID.equals(tenantId);
    }
}

