package com.pusula.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a tenant attempts to use a feature not included in their plan.
 * Returns HTTP 403 with plan upgrade guidance.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class FeatureNotAvailableException extends RuntimeException {
    private final String featureKey;
    private final String currentPlan;

    public FeatureNotAvailableException(String featureKey, String currentPlan) {
        super("Bu özellik (" + featureKey + ") mevcut paketinizde (" + currentPlan + ") bulunmamaktadır. " +
              "Lütfen paketinizi yükseltin.");
        this.featureKey = featureKey;
        this.currentPlan = currentPlan;
    }

    public String getFeatureKey() { return featureKey; }
    public String getCurrentPlan() { return currentPlan; }
}
