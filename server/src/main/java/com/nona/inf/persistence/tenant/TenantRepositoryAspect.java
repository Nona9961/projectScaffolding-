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
 * 基于 Hibernate discriminator multi-tenancy（@TenantId）实现 tenant-scoped 的自动隔离（读 fail-closed；写自动注入/校验 tenantID）。
 *
 * @author nona
 */
@Aspect
@Component
@RequiredArgsConstructor
public class TenantRepositoryAspect {

    private final TenantContextAccessor tenantContextAccessor;

    /**
     * 对 Spring Data Repository 的写入操作（save/saveAll）应用租户规则。
     *
     * @param joinPoint AOP 连接点
     * @return 原方法返回值
     * @throws Throwable 当下游调用抛出异常时透传
     */
    @Around("this(org.springframework.data.repository.Repository)")
    public Object applyTenantRules(ProceedingJoinPoint joinPoint) throws Throwable {
        enforceTenantWriteIfNeeded(joinPoint);
        return joinPoint.proceed();
    }

    /**
     * 若当前调用为写入操作（save/saveAll），则对参数应用租户写入门禁。
     *
     * @param joinPoint AOP 连接点
     */
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

    /**
     * 对 tenant-scoped 实体执行写入门禁：
     * <p>
     * - cross-tenant：必须显式提供 entity tenantID
     * - non cross-tenant：当前 tenant 缺失则拒绝；entity tenantID 缺失则注入；不一致则拒绝
     *
     * @param entity 待写入的实体对象
     * @throws com.nona.exceptions.BusinessException 当租户规则校验失败时抛出
     */
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

    /**
     * 规范化 tenantID：{@code null} 或空白字符串视为缺失。
     *
     * @param tenantID 原始 tenantID
     * @return 规范化后的 tenantID；缺失则返回 {@code null}
     */
    private static String normalizeTenantID(String tenantID) {
        if (tenantID == null || tenantID.isBlank()) {
            return null;
        }
        return tenantID;
    }
}