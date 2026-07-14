package com.pusula.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushPlatform;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.PushDeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TicketPushNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(TicketPushNotificationListener.class);
    private final PushDeviceRepository repository;
    private final PushTokenCrypto tokenCrypto;
    private final ApnsGateway gateway;
    private final ApplePushProperties properties;
    private final ObjectMapper objectMapper;

    public TicketPushNotificationListener(PushDeviceRepository repository, PushTokenCrypto tokenCrypto,
            ApnsGateway gateway, ApplePushProperties properties, ObjectMapper objectMapper) {
        this.repository = repository;
        this.tokenCrypto = tokenCrypto;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Async
    @Transactional
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketAssigned(TicketAssignedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        String payload;
        try {
            payload = payload(event.ticketId());
        } catch (JsonProcessingException ex) {
            log.error("Ticket push payload could not be created for ticketId={}", event.ticketId());
            return;
        }

        for (PushDevice device : repository.findByCompanyIdAndUserIdAndActiveTrueAndPlatform(
                event.companyId(), event.technicianId(), PushPlatform.IOS)) {
            try {
                String token = tokenCrypto.decrypt(device.getTokenCiphertext());
                ApnsDeliveryResult result = gateway.send(token, device.getEnvironment(), payload);
                if (result.invalidToken()) {
                    device.setActive(false);
                    repository.save(device);
                    log.info("APNs device deactivated after permanent rejection: deviceId={}, reason={}",
                            device.getId(), result.rejectionReason());
                } else if (!result.accepted() && !result.disabled()) {
                    log.warn("APNs notification rejected: deviceId={}, ticketId={}, reason={}",
                            device.getId(), event.ticketId(), result.rejectionReason());
                }
            } catch (Exception ex) {
                // Never include the token/ciphertext or exception message: providers and
                // crypto libraries may echo sensitive input in exception details.
                log.warn("APNs notification failed safely: deviceId={}, ticketId={}, errorType={}",
                        device.getId(), event.ticketId(), ex.getClass().getSimpleName());
            }
        }
    }

    private String payload(Long ticketId) throws JsonProcessingException {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", "Yeni iş atandı");
        alert.put("body", "Yeni bir servis işi hesabınıza atandı.");

        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("aps", aps);
        root.put("type", "TICKET_ASSIGNED");
        root.put("ticketId", ticketId);
        return objectMapper.writeValueAsString(root);
    }
}
