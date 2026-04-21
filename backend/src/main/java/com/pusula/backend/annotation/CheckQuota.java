package com.pusula.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method as requiring a quota check before execution.
 * The FeatureGateAspect will verify the tenant hasn't exceeded their
 * plan's limit for the specified usage type.
 * 
 * Example: @CheckQuota("TICKETS") on createTicket() will prevent
 * ticket creation when the monthly limit is reached.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CheckQuota {
    String value();
}
