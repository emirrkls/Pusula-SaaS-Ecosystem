package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.entity.Notification;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushPlatform;
import com.pusula.backend.event.AdminNotificationCreatedEvent;
import com.pusula.backend.repository.NotificationRepository;
import com.pusula.backend.repository.PushDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class AdminPushNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(AdminPushNotificationListener.class);
    private final NotificationRepository notificationRepository;
    private final PushDeviceRepository pushDeviceRepository;
    private final PushTokenCrypto tokenCrypto;
    private final ApnsGateway gateway;
    private final ApplePushProperties properties;
    private final ObjectMapper objectMapper;

    public AdminPushNotificationListener(NotificationRepository notificationRepository,
            PushDeviceRepository pushDeviceRepository, PushTokenCrypto tokenCrypto,
            ApnsGateway gateway, ApplePushProperties properties, ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.pushDeviceRepository = pushDeviceRepository;
        this.tokenCrypto = tokenCrypto;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onNotification(AdminNotificationCreatedEvent event) {
        if (!properties.isEnabled()) return;
        Notification notification = notificationRepository.findByIdAndCompanyIdAndUserId(
                event.notificationId(), event.companyId(), event.userId()).orElse(null);
        if (notification == null) return;
        List<PushDevice> devices = pushDeviceRepository.findByCompanyIdAndUserIdAndActiveTrueAndPlatform(
                event.companyId(), event.userId(), PushPlatform.IOS);
        if (devices.isEmpty()) return;
        String payload;
        try {
            payload = payload(notification, notificationRepository.countByCompanyIdAndUserIdAndIsReadFalse(
                    event.companyId(), event.userId()));
        } catch (Exception ex) {
            log.warn("Admin push payload could not be created: notificationId={}", event.notificationId());
            return;
        }
        for (PushDevice device : devices) {
            try {
                ApnsDeliveryResult result = gateway.send(tokenCrypto.decrypt(device.getTokenCiphertext()),
                        device.getEnvironment(), payload);
                if (result.invalidToken()) {
                    device.setActive(false);
                    pushDeviceRepository.save(device);
                } else if (!result.accepted() && !result.disabled()) {
                    log.warn("Admin push rejected: deviceId={}, notificationId={}, reason={}",
                            device.getId(), notification.getId(), result.rejectionReason());
                }
            } catch (Exception ex) {
                log.warn("Admin push failed safely: deviceId={}, notificationId={}, errorType={}",
                        device.getId(), notification.getId(), ex.getClass().getSimpleName());
            }
        }
    }

    private String payload(Notification notification, long unreadCount) throws Exception {
        Map<String, Object> alert = Map.of("title", notification.getTitle(), "body", notification.getMessage());
        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");
        aps.put("badge", Math.min(unreadCount, 99));
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("aps", aps);
        root.put("type", "ADMIN_NOTIFICATION");
        root.put("notificationId", notification.getId());
        root.put("category", notification.getCategory().name());
        if ("TICKET".equals(notification.getReferenceType()) && notification.getReferenceId() != null) {
            root.put("ticketId", notification.getReferenceId());
        }
        return objectMapper.writeValueAsString(root);
    }
}
