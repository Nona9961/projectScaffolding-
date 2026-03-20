package com.nona.inf.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 跨租户操作的显式放行标记（ADR-001）。
 * <p>
 * 默认路径仍走 tenant 注入；只有在显式标注/开启时，才允许跨租户查询/写入。
 *
 * @author nona
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CrossTenant {
}

