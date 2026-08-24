package com.pusula.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushPlatform;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.PushDeviceRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class TicketPushNotificationListener {
    private static final Logger log = LoggerFactory.getLogger(TicketPushNotificationListener.class);
    private final PushDeviceRepository repository;
    private final ServiceTicketRepository ticketRepository;
    private final PushTokenCrypto tokenCrypto;
    private final ApnsGateway gateway;
    private final ApplePushProperties properties;
    private final ObjectMapper objectMapper;
    private final ZoneId businessZone;
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern(
            "d MMMM EEEE HH:mm", Locale.forLanguageTag("tr-TR"));

    public TicketPushNotificationListener(PushDeviceRepository repository, ServiceTicketRepository ticketRepository,
            PushTokenCrypto tokenCrypto, ApnsGateway gateway, ApplePushProperties properties,
            ObjectMapper objectMapper, @org.springframework.beans.factory.annotation.Value("${app.business.timezone:Europe/Istanbul}") String businessZone) {
        this.repository = repository;
        this.ticketRepository = ticketRepository;
        this.tokenCrypto = tokenCrypto;
        this.gateway = gateway;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.businessZone = ZoneId.of(businessZone);
    }

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTicketAssigned(TicketAssignedEvent event) {
        if (!properties.isEnabled()) {
            return;
        }
        ServiceTicket ticket = ticketRepository.findByIdAndCompanyIdForUpdate(event.ticketId(), event.companyId())
                .orElse(null);
        if (ticket == null
                || !event.technicianId().equals(ticket.getAssignedTechnicianId())
                || ticket.getAssignmentNotificationSentAt() != null
                || !isOpen(ticket)
                || !isWithinNotificationWindow(ticket)) {
            return;
        }
        String payload;
        try {
            payload = payload(ticket);
        } catch (JsonProcessingException ex) {
            log.error("Ticket push payload could not be created for ticketId={}", event.ticketId());
            return;
        }

        List<PushDevice> devices = repository.findByCompanyIdAndUserIdAndActiveTrueAndPlatform(
                event.companyId(), event.technicianId(), PushPlatform.IOS);
        if (devices.isEmpty()) {
            return;
        }
        boolean deliveryProcessed = false;
        for (PushDevice device : devices) {
            try {
                String token = tokenCrypto.decrypt(device.getTokenCiphertext());
                ApnsDeliveryResult result = gateway.send(token, device.getEnvironment(), payload);
                deliveryProcessed = deliveryProcessed || !result.disabled();
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
        if (deliveryProcessed) {
            ticket.setAssignmentNotificationSentAt(LocalDateTime.now(businessZone));
            ticketRepository.save(ticket);
        }
    }

    private boolean isOpen(ServiceTicket ticket) {
        return ticket.getStatus() == ServiceTicket.TicketStatus.ASSIGNED
                || ticket.getStatus() == ServiceTicket.TicketStatus.IN_PROGRESS;
    }

    private boolean isWithinNotificationWindow(ServiceTicket ticket) {
        return ticket.getScheduledDate() == null
                || !ticket.getScheduledDate().isAfter(LocalDateTime.now(businessZone).plusHours(24));
    }

    private String payload(ServiceTicket ticket) throws JsonProcessingException {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("title", "Yeni iş atandı");
        alert.put("body", notificationBody(ticket));

        Map<String, Object> aps = new LinkedHashMap<>();
        aps.put("alert", alert);
        aps.put("sound", "default");

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("aps", aps);
        root.put("type", "TICKET_ASSIGNED");
        root.put("ticketId", ticket.getId());
        return objectMapper.writeValueAsString(root);
    }

    private String notificationBody(ServiceTicket ticket) {
        if (ticket.getScheduledDate() == null) {
            return "Yeni bir servis işi hesabınıza atandı.";
        }
        StringBuilder body = new StringBuilder("Servis zamanı: ")
                .append(ticket.getScheduledDate().format(DATE_TIME_FORMAT));
        if (ticket.getScheduledEndDate() != null) {
            body.append("–").append(ticket.getScheduledEndDate().format(DateTimeFormatter.ofPattern("HH:mm")));
        }
        return body.toString();
    }
}
