package com.nona.inf.persistence.tenant;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.context.TenantContextAccessor;
import com.nona.inf.context.TenantPrivilege;
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
 * 写门禁判断源为 {@link com.nona.inf.context.TenantPrivilege}：提权作用域内放行实体显式合法租户，非提权状态强制与当前租户一致。
 *
 * @author nona9961
 */
@Aspect
@Component
@RequiredArgsConstructor
@ScaffoldGenerated
public class TenantRepositoryAspect {

    private final TenantContextAccessor tenantContextAccessor;

    /**
     * 存储无关的租户读隔离适配层（JPA 实现）：每次数据访问前按当前状态启停租户 filter。
     */
    private final TenantReadIsolationAdapter tenantReadIsolationAdapter;

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

    /**
     * 对 Spring Data Repository 的所有访问应用租户规则：
     * 先由适配层应用读隔离状态（读放行/恢复过滤），再对写入操作执行写门禁。
     *
     * @param joinPoint AOP 连接点
     * @return 原方法返回值
     * @throws Throwable 当下游调用抛出异常时透传
     */
    @Around("this(org.springframework.data.repository.Repository)")
    public Object applyTenantRules(ProceedingJoinPoint joinPoint) throws Throwable {
        tenantReadIsolationAdapter.applyReadIsolation();
        enforceTenantWriteIfNeeded(joinPoint);
        return joinPoint.proceed();
    }

    /**
     * 若当前调用为写入操作（save/saveAndFlush/saveAll/saveAllAndFlush），则对参数应用租户写入门禁。
     *
     * @param joinPoint AOP 连接点
     */
    private void enforceTenantWriteIfNeeded(ProceedingJoinPoint joinPoint) {
        final String methodName = joinPoint.getSignature().getName();
        if ("save".equals(methodName) || "saveAndFlush".equals(methodName)) {
            final Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return;
            }
            ensureAndInjectTenantID(args[0]);
            return;
        }

        if ("saveAll".equals(methodName) || "saveAllAndFlush".equals(methodName)) {
            final Object[] args = joinPoint.getArgs();
            if (args == null || args.length == 0) {
                return;
            }
            final Object first = args[0];
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
     * - 提权作用域内：实体显式携带合法 tenantID 则放行并保留原值（系统编排/superAdmin/C 端跨店下单场景，
     *   允许上下文无租户）；实体未携带则回退注入当前 tenant（admin 代建场景），此时当前 tenant 必须存在
     * - 非提权状态：当前 tenant 必须存在；entity tenantID 缺失则注入，不一致则拒绝
     *
     * @param entity 待写入的实体对象
     * @throws com.nona.exceptions.BusinessException 当租户规则校验失败时抛出
     */
    private void ensureAndInjectTenantID(Object entity) {
        if (!(entity instanceof TenantScopedBasePO tenantScopedPO)) {
            return;
        }

        final String entityTenantID = normalizeTenantID(tenantScopedPO.getTenantID());
        final String tenantID = tenantContextAccessor.getTenantID();

        if (TenantPrivilege.isActive()) {
            if (entityTenantID != null) {
                BusinessAssert.assertTrue(!TenantContextAccessor.MISSING_TENANT_ID.equals(entityTenantID),
                        "invalid tenantID: {}", entityTenantID);
                return;
            }
            BusinessAssert.assertNonNull(tenantID,
                    "tenantID is required to inject into tenant-scoped write");
            tenantScopedPO.setTenantID(tenantID);
            return;
        }

        BusinessAssert.assertNonNull(tenantID, "tenantID is required for tenant-scoped write operation");

        if (entityTenantID == null) {
            tenantScopedPO.setTenantID(tenantID);
            return;
        }

        BusinessAssert.assertTrue(tenantID.equals(entityTenantID),
                "cross-tenant write is forbidden. currentTenant={}, entityTenant={}", tenantID, entityTenantID);
    }
}
