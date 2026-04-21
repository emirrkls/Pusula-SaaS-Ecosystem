package com.pusula.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint or service method as requiring a specific feature flag.
 * The FeatureGateAspect intercepts calls and checks the tenant's plan.
 * 
 * Example: @RequiresFeature("FINANCE_MODULE") on a controller method
 * will return 403 for CIRAK plan users.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresFeature {
    String value();
}
