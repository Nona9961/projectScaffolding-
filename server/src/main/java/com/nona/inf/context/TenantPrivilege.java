package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Callable;

/**
 * 租户提权作用域：作用域内允许实体显式携带异租户值写入、读过滤由持久化适配层放行。
 * 基于 ScopedValue：出作用域自动恢复、块内不可篡改、默认不跨线程传播。
 *
 * @author nona9961
 */
@Slf4j
@ScaffoldGenerated
public final class TenantPrivilege {

    private static final ScopedValue<Boolean> ELEVATED = ScopedValue.newInstance();

    /**
     * 提权作用域生命周期监听器（持久化适配层注册，用于读路径 filter 切换）；{@code null} 表示未注册。
     */
    private static volatile TenantScopeListener tenantScopeListener;

    /**
     * 租户上下文访问器（Spring 组件初始化时注册，审计日志取 identity/tenantID 用）；{@code null} 表示未注册。
     */
    private static volatile TenantContextAccessor tenantContextAccessor;

    /**
     * 私有构造：纯静态工具类，禁止实例化。
     */
    private TenantPrivilege() {
    }

    /**
     * 进入提权作用域执行无返回值操作。
     *
     * @param action 提权操作
     */
    public static void elevated(Runnable action) {
        log.info("[TenantPrivilege] elevated scope enter, action={}, alreadyActive={}, identity={}, tenantID={}, at={}",
                action.getClass().getSimpleName(), isActive(), resolveIdentity(), resolveTenantID(), Instant.now());
        notifyListenerEnter();
        try {
            ScopedValue.where(ELEVATED, Boolean.TRUE).run(action);
        }
        finally {
            notifyListenerExit();
        }
    }

    /**
     * 进入提权作用域执行有返回值操作。
     *
     * @param action 提权操作
     * @param <T>    返回类型
     * @return 操作结果
     * @throws Exception 操作抛出的异常原样透传
     */
    public static <T> T elevated(Callable<T> action) throws Exception {
        log.info("[TenantPrivilege] elevated scope enter, action={}, alreadyActive={}, identity={}, tenantID={}, at={}",
                action.getClass().getSimpleName(), isActive(), resolveIdentity(), resolveTenantID(), Instant.now());
        notifyListenerEnter();
        try {
            return ScopedValue.where(ELEVATED, Boolean.TRUE).call(action::call);
        }
        finally {
            notifyListenerExit();
        }
    }

    /**
     * 先进入提权作用域再开启事务——保证 session 创建时租户模式已定型。
     * 提权绑定与事务边界合并为同一结构化作用域；事务回调内抛出的 {@link RuntimeException}
     * （含 {@link com.nona.exceptions.BusinessException}）原样透传，仅受检异常包装为
     * {@link IllegalStateException}。
     *
     * @param transactionTemplate 事务模板
     * @param action              提权事务操作
     * @param <T>                 返回类型
     * @return 事务执行结果
     * @throws Exception 操作抛出的异常原样透传
     */
    public static <T> T elevatedInTransaction(TransactionTemplate transactionTemplate,
                                              Callable<T> action) throws Exception {
        return elevated(() -> transactionTemplate.execute(status -> {
            try {
                return action.call();
            }
            catch (RuntimeException e) {
                throw e;
            }
            catch (Exception e) {
                throw new IllegalStateException("elevated transaction failed", e);
            }
        }));
    }

    /**
     * 当前是否处于提权作用域。
     *
     * @return 提权中返回 true
     */
    public static boolean isActive() {
        return ELEVATED.isBound() && ELEVATED.get();
    }

    /**
     * 注册提权作用域生命周期监听器；传 {@code null} 忽略（幂等）。
     *
     * @param listener 监听器；{@code null} 忽略
     */
    public static void registerTenantScopeListener(TenantScopeListener listener) {
        if (listener != null) {
            tenantScopeListener = listener;
        }
    }

    /**
     * 注册租户上下文访问器（供审计日志解析调用者身份与当前租户）；传 {@code null} 忽略（幂等）。
     *
     * @param accessor 访问器；{@code null} 忽略
     */
    public static void registerTenantContextAccessor(TenantContextAccessor accessor) {
        if (accessor != null) {
            tenantContextAccessor = accessor;
        }
    }

    /**
     * 通知监听器进入提权作用域（ScopedValue 绑定前）。
     */
    private static void notifyListenerEnter() {
        if (tenantScopeListener != null) {
            tenantScopeListener.onElevatedEnter();
        }
    }

    /**
     * 通知监听器退出提权作用域（ScopedValue 解绑后，本层已解绑）。
     */
    private static void notifyListenerExit() {
        if (tenantScopeListener != null) {
            tenantScopeListener.onElevatedExit();
        }
    }

    /**
     * 解析审计日志用的调用者身份；访问器未注册或身份缺失时返回 {@code "unknown"}。
     *
     * @return 调用者身份；不可用时返回 {@code "unknown"}
     */
    private static String resolveIdentity() {
        if (tenantContextAccessor == null) {
            return "unknown";
        }
        return Objects.requireNonNullElse(tenantContextAccessor.getIdentity(), "unknown");
    }

    /**
     * 解析审计日志用的当前租户；访问器未注册或租户缺失时返回 {@code "unknown"}。
     *
     * @return 当前租户；不可用时返回 {@code "unknown"}
     */
    private static String resolveTenantID() {
        if (tenantContextAccessor == null) {
            return "unknown";
        }
        return Objects.requireNonNullElse(tenantContextAccessor.getTenantID(), "unknown");
    }
}
