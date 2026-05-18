package com.nona.inf.context;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import com.nona.annotation.ScaffoldGenerated;

/**
 * 跨租户操作的显式放行标记（ADR-001）。
 *
 * @author nona
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@ScaffoldGenerated
public @interface CrossTenant {
}

