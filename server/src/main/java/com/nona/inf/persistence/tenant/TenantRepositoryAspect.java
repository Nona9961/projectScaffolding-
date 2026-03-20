package com.nona.inf.persistence.tenant;

import com.nona.inf.context.TenantContextAccessor;
import com.nona.inf.persistence.po.TenantScopedBasePO;
import com.nona.util.BusinessAssert;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 多租户隔离（ADR-001 / ADR-007）
 * <p>
 * 基于 Hibernate Filter 实现 tenant-scoped 的自动隔离（读 fail-closed；写自动注入/校验 tenantID）。
 *
 * @author nona
 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantRepositoryAspect {

    private final TenantContextAccessor tenantContextAccessor;

    @Around("this(org.springframework.data.repository.Repository)")
    public Object applyTenantRules(ProceedingJoinPoint joinPoint) throws Throwable {
        enforceTenantWriteIfNeeded(joinPoint);
        return joinPoint.proceed();
    }

    private void enforceTenantWriteIfNeeded(ProceedingJoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        if ("save".equals(methodName)) {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return;
            }
            ensureAndInjectTenantID(args[0]);
            return;
        }

        if ("saveAll".equals(methodName)) {
            Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return;
            }
            Object first = args[0];
            if (!(first instanceof Iterable<?> iterable)) {
                return;
            }
            for (Object entity : iterable) {
                ensureAndInjectTenantID(entity);
            }
        }
    }

    private void ensureAndInjectTenantID(Object entity) {
        if (!(entity instanceof TenantScopedBasePO tenantScopedPO)) {
            return;
        }

        if (tenantContextAccessor.isCrossTenant()) {
            String entityTenantID = normalizeTenantID(tenantScopedPO.getTenantID());
            BusinessAssert.assertTrue(entityTenantID != null, "tenantID is required for cross-tenant write operation");
            BusinessAssert.assertTrue(!TenantContextAccessor.MISSING_TENANT_ID.equals(entityTenantID), "invalid tenantID: {}", entityTenantID);
            return;
        }

        String tenantID = tenantContextAccessor.getTenantID();
        BusinessAssert.assertNonNull(tenantID, "tenantID is required for tenant-scoped write operation");
        BusinessAssert.assertTrue(!TenantContextAccessor.MISSING_TENANT_ID.equals(tenantID), "invalid tenantID: {}", tenantID);

        String entityTenantID = normalizeTenantID(tenantScopedPO.getTenantID());
        if (entityTenantID == null) {
            tenantScopedPO.setTenantID(tenantID);
            return;
        }

        BusinessAssert.assertTrue(tenantID.equals(entityTenantID),
                "cross-tenant write is forbidden. currentTenant={}, entityTenant={}", tenantID, entityTenantID);
    }

    private static String normalizeTenantID(String tenantID) {
        if (tenantID == null || tenantID.isBlank()) {
            return null;
        }
        return tenantID;
    }
}
