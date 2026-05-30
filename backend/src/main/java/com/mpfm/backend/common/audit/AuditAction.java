package com.mpfm.backend.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * AuditAction 审计注解，声明操作审计元数据。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditAction {

    String action() default "";

    String target() default "unknown";

    String riskLevel() default "normal";

    AuditEvent event() default AuditEvent.DEMO_SUCCESS;
}





