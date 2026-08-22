package com.nona.inf.persistence.tenant;

import com.nona.annotation.ScaffoldGenerated;
import com.nona.inf.context.TenantContextAccessor;
import com.nona.inf.context.TenantPrivilege;
import com.nona.inf.context.TenantScopeListener;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 提权作用域读路径第二重保险：线程已绑定 EntityManager（已定型 session）时，进入提权
 * 作用域禁用 Hibernate 租户 filter、退出时恢复，使已定型 session 内的提权查询绕过单租户过滤。
 * <p>
 * 无绑定 EM（无事务/无 session）时不动作——该场景由 resolver 第一重保险（session 打开时
 * 按 {@link TenantPrivilege#isActive()} 解析 root 租户）覆盖。本实现无状态，仅现场判断，
 * 天然线程安全，可重入（嵌套提权时最外层退出才恢复）。
 *
 * @author nona9961
 */
@Component
@RequiredArgsConstructor
@ScaffoldGenerated
public class JpaTenantScopeListener implements TenantScopeListener {

    /**
     * Hibernate {@code @TenantId} 租户 filter 名称；对应
     * {@code org.hibernate.binder.internal.TenantIdBinder.FILTER_NAME}（内部类无法直接引用，按源码查证写死）。
     */
    private static final String FILTER_NAME = "_tenantId";

    /**
     * 租户 filter 参数名；对应
     * {@code org.hibernate.binder.internal.TenantIdBinder.PARAMETER_NAME}（内部类无法直接引用，按源码查证写死）。
     */
    private static final String PARAMETER_NAME = "tenantId";

    private final EntityManagerFactory entityManagerFactory;

    private final TenantContextAccessor tenantContextAccessor;

    /**
     * 注册本监听器到 {@link TenantPrivilege}，并捎带注册租户上下文访问器（供提权审计日志解析身份与租户）。
     */
    @PostConstruct
    void registerToTenantPrivilege() {
        TenantPrivilege.registerTenantScopeListener(this);
        TenantPrivilege.registerTenantContextAccessor(tenantContextAccessor);
    }

    /**
     * 进入提权作用域：线程已绑定 EntityManager 时禁用 Hibernate 租户 filter（已定型 session 场景第二重保险）。
     */
    @Override
    public void onElevatedEnter() {
        if (!TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
            return;
        }
        final EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
        holder.getEntityManager().unwrap(Session.class).disableFilter(FILTER_NAME);
    }

    /**
     * 退出提权作用域：仍处于外层提权（嵌套场景）时不动作；最外层退出且线程仍绑定
     * EntityManager 时重新启用租户 filter 并恢复原租户参数。
     * <p>
     * {@link org.hibernate.Filter#setParameter} 必须显式调用——Hibernate re-enable 返回的
     * filter 参数为空（已知行为），不设参则过滤条件无法构造。
     */
    @Override
    public void onElevatedExit() {
        if (TenantPrivilege.isActive()) {
            return;
        }
        if (!TransactionSynchronizationManager.hasResource(entityManagerFactory)) {
            return;
        }
        final EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(entityManagerFactory);
        holder.getEntityManager().unwrap(Session.class)
                .enableFilter(FILTER_NAME)
                .setParameter(PARAMETER_NAME, tenantContextAccessor.getTenantIDOrMissing());
    }
}
