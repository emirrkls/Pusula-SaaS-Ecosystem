package com.pusula.backend.job;

import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketNotificationSchedulerTest {
    @Test
    void publishesAssignmentsThatEnteredThe24HourWindow() {
        ServiceTicketRepository repository = mock(ServiceTicketRepository.class);
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        ServiceTicket ticket = ServiceTicket.builder().id(30L).companyId(10L).assignedTechnicianId(7L)
                .status(ServiceTicket.TicketStatus.ASSIGNED).build();
        when(repository.findAssignmentsDueForNotification(any(LocalDateTime.class))).thenReturn(List.of(ticket));

        new TicketNotificationScheduler(repository, publisher, "Europe/Istanbul").publishDueAssignments();

        verify(publisher).publishEvent(new TicketAssignedEvent(10L, 7L, 30L));
    }
}
