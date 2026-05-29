package com.example.medical.common.audit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String module();
    String action();

    /**
     * Whether this method accesses or mutates Protected Health Information.
     * When {@code true}, method parameter values are masked as [PHI] in the
     * audit detail field to prevent ePHI from leaking into audit logs.
     */
    boolean phiAccess() default false;
}
