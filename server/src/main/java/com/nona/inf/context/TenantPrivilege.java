package com.nona.inf.context;

import com.nona.annotation.ScaffoldGenerated;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.concurrent.Callable;

/**
 * 租户提权作用域：作用域内允许实体显式携带异租户值写入、读过滤由持久化适配层放行。
 * 基于 ScopedValue：出作用域自动恢复、块内不可篡改、默认不跨线程传播。
 *
 * @author nona
 */
@Slf4j
@ScaffoldGenerated
public final class TenantPrivilege {

    private static final ScopedValue<Boolean> ELEVATED = ScopedValue.newInstance();

    private TenantPrivilege() {
    }

    /**
     * 进入提权作用域执行无返回值操作。
     *
     * @param action 提权操作
     */
    public static void elevated(Runnable action) {
        log.info("[TenantPrivilege] elevated scope enter, action={}, alreadyActive={}, at={}",
                action.getClass().getSimpleName(), isActive(), Instant.now());
        ScopedValue.where(ELEVATED, Boolean.TRUE).run(action);
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
        log.info("[TenantPrivilege] elevated scope enter, action={}, alreadyActive={}, at={}",
                action.getClass().getSimpleName(), isActive(), Instant.now());
        return ScopedValue.where(ELEVATED, Boolean.TRUE).call(action::call);
    }

    /**
     * 先进入提权作用域再开启事务——保证 session 创建时租户模式已定型。
     *
     * @param transactionTemplate 事务模板
     * @param action              提权事务操作
     * @param <T>                 返回类型
     * @return 事务执行结果
     */
    public static <T> T elevatedInTransaction(TransactionTemplate transactionTemplate,
                                              Callable<T> action) throws Exception {
        return elevated(() -> transactionTemplate.execute(status -> {
            try {
                return action.call();
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
}
