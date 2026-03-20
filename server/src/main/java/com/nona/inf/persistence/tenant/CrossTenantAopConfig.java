package com.nona.inf.persistence.tenant;

import com.nona.inf.context.CrossTenant;
import com.nona.inf.context.TenantContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 为 {@link CrossTenant} 提供基于 Spring AOP（非 AspectJ）的拦截器：
 * 在当前线程临时开启 cross-tenant 模式，用于绕过默认 tenant 注入。
 */
@Configuration
public class CrossTenantAopConfig {

    @Bean
    public static DefaultAdvisorAutoProxyCreator defaultAdvisorAutoProxyCreator() {
        DefaultAdvisorAutoProxyCreator creator = new DefaultAdvisorAutoProxyCreator();
        creator.setProxyTargetClass(true);
        return creator;
    }

    @Bean
    public Advisor crossTenantAdvisor() {
        AnnotationMatchingPointcut pointcut = new AnnotationMatchingPointcut(CrossTenant.class, CrossTenant.class, true);
        MethodInterceptor interceptor = invocation -> {
            boolean previous = TenantContext.isCrossTenant();
            TenantContext.setCrossTenant(true);
            try {
                return invocation.proceed();
            } finally {
                TenantContext.setCrossTenant(previous);
            }
        };
        return new DefaultPointcutAdvisor(pointcut, interceptor);
    }
}

