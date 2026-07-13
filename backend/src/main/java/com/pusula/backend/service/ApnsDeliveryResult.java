package com.pusula.backend.service;

public record ApnsDeliveryResult(boolean accepted, boolean invalidToken, boolean disabled, String rejectionReason) {
    public static ApnsDeliveryResult acceptedResult() {
        return new ApnsDeliveryResult(true, false, false, null);
    }

    public static ApnsDeliveryResult disabledResult() {
        return new ApnsDeliveryResult(false, false, true, null);
    }

    public static ApnsDeliveryResult rejected(String reason) {
        boolean invalid = "BadDeviceToken".equals(reason)
                || "Unregistered".equals(reason)
                || "DeviceTokenNotForTopic".equals(reason);
        return new ApnsDeliveryResult(false, invalid, false, reason);
    }
}
