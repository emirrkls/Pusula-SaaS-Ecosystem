package com.pusula.backend.job;

import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class TicketNotificationScheduler {
    private final ServiceTicketRepository ticketRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ZoneId businessZone;

    public TicketNotificationScheduler(ServiceTicketRepository ticketRepository,
            ApplicationEventPublisher eventPublisher,
            @Value("${app.business.timezone:Europe/Istanbul}") String businessZone) {
        this.ticketRepository = ticketRepository;
        this.eventPublisher = eventPublisher;
        this.businessZone = ZoneId.of(businessZone);
    }

    /** Publishes assignments once they enter the rolling 24-hour notification window. */
    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void publishDueAssignments() {
        LocalDateTime cutoff = LocalDateTime.now(businessZone).plusHours(24);
        ticketRepository.findAssignmentsDueForNotification(cutoff).forEach(ticket ->
                eventPublisher.publishEvent(new TicketAssignedEvent(
                        ticket.getCompanyId(), ticket.getAssignedTechnicianId(), ticket.getId())));
    }
}
