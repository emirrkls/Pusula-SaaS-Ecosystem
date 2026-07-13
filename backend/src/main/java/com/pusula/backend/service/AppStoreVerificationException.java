package com.pusula.backend.service;

public class AppStoreVerificationException extends RuntimeException {

    private final Reason reason;

    public AppStoreVerificationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        CONFIGURATION,
        VERIFICATION_FAILED,
        MALFORMED,
        PRODUCT_NOT_ALLOWED,
        BUNDLE_MISMATCH,
        ENVIRONMENT_NOT_ALLOWED,
        NOT_SUBSCRIPTION,
        REVOKED,
        EXPIRED,
        OWNERSHIP_CONFLICT
    }
}
