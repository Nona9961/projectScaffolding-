package com.nona.inf.persistence.tenant;

import com.nona.inf.context.CrossTenant;
import com.nona.inf.context.TenantContextAccessor;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 为 {@link CrossTenant} 提供基于 Spring AOP 的拦截器：
 * 在当前线程临时开启 cross-tenant 模式，用于绕过默认 tenant 隔离。
 *
 * @author nona
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class CrossTenantAspect {

    private final TenantContextAccessor tenantContextAccessor;

    /**
     * 在 {@link CrossTenant} 标注的方法/类作用域内临时开启跨租户模式，并在退出后恢复现场。
     *
     * @param joinPoint AOP 连接点
     * @return 原方法返回值
     * @throws Throwable 当下游调用抛出异常时透传
     */
    @Around("@within(com.nona.inf.context.CrossTenant) || @annotation(com.nona.inf.context.CrossTenant)")
    public Object withCrossTenant(ProceedingJoinPoint joinPoint) throws Throwable {
        boolean previous = tenantContextAccessor.isCrossTenant();
        tenantContextAccessor.setCrossTenant(true);
        try {
            return joinPoint.proceed();
        } finally {
            tenantContextAccessor.setCrossTenant(previous);
        }
    }
}