package com.pusula.backend.service;

import com.pusula.backend.entity.AuditLog;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.AuditLogRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTicketTimelineTest {
    @Mock AuditLogRepository auditLogRepository;
    @Mock FeatureService featureService;
    @Mock ServiceTicketRepository ticketRepository;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void normalTicketLogCapturesAuthenticatedActorBeforeRequestContextIsLost() {
        User user = new User();
        user.setId(7L);
        user.setCompanyId(10L);
        user.setUsername("teknisyen");
        user.setFullName("Uğur Yıldırım");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        AuditLogService service = new AuditLogService(auditLogRepository, featureService);

        service.log("UPDATE", "TICKET", 888L, "Servis tamamlandı");

        verify(auditLogRepository).save(argThat(log -> log.getCompanyId().equals(10L)
                && log.getUserId().equals(7L) && log.getEntityId().equals(888L)));
    }

    @Test
    void legacyCompletedTicketGetsCreationAndCompletionTimelineFallbacks() {
        AuditLogService service = new AuditLogService(auditLogRepository, featureService);
        ReflectionTestUtils.setField(service, "serviceTicketRepository", ticketRepository);
        ServiceTicket ticket = ServiceTicket.builder().id(888L).companyId(10L).customerId(20L)
                .status(ServiceTicket.TicketStatus.COMPLETED).description("Klima arıza").build();
        ticket.setCreatedAt(LocalDateTime.of(2026, 9, 4, 12, 16));
        ticket.setCompletedAt(LocalDateTime.of(2026, 9, 10, 17, 19));
        when(featureService.getAuditRetentionDays(10L)).thenReturn(null);
        when(auditLogRepository.findByCompanyIdAndEntityTypeAndEntityIdOrderByTimestampAsc(
                10L, "TICKET", 888L)).thenReturn(List.of());
        when(ticketRepository.findById(888L)).thenReturn(Optional.of(ticket));

        List<AuditLog> timeline = service.getTicketTimeline(10L, 888L);

        assertEquals(List.of("CREATE", "COMPLETE"), timeline.stream().map(AuditLog::getActionType).toList());
    }
}
