package com.pusula.backend.event;

public record AdminNotificationCreatedEvent(Long companyId, Long userId, Long notificationId) {}
