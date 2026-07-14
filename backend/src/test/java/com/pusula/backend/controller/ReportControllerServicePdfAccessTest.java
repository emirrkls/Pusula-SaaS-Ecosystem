package com.pusula.backend.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportControllerServicePdfAccessTest {

    private final ServiceTicketRepository ticketRepository = mock(ServiceTicketRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ReportService reportService = mock(ReportService.class);
    private final ReportController controller = new ReportController(ticketRepository, userRepository, reportService);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void technicianCannotDownloadAnotherTechniciansTicket() {
        authenticate(7L, 10L, "TECHNICIAN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket(100L, 10L, 8L)));

        ResponseEntity<byte[]> response = controller.downloadServiceReport(100L);

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        verify(reportService, never()).generateServiceReport(100L);
    }

    @Test
    void foreignTenantTicketLooksNotFound() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket(100L, 20L, null)));

        ResponseEntity<byte[]> response = controller.downloadServiceReport(100L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(reportService, never()).generateServiceReport(100L);
    }

    @Test
    void missingTicketReturnsNotFound() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        when(ticketRepository.findById(404L)).thenReturn(Optional.empty());

        ResponseEntity<byte[]> response = controller.downloadServiceReport(404L);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        verify(reportService, never()).generateServiceReport(404L);
    }

    @Test
    void pdfFailureLogDoesNotIncludeExceptionMessageOrTicketData() {
        authenticate(1L, 10L, "COMPANY_ADMIN");
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket(100L, 10L, null)));
        when(reportService.generateServiceReport(100L))
                .thenThrow(new RuntimeException("sensitive customer detail"));

        Logger logger = (Logger) LoggerFactory.getLogger(ReportController.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            ResponseEntity<byte[]> response = controller.downloadServiceReport(100L);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
            String messages = appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (left, right) -> left + "\n" + right);
            assertTrue(messages.contains("errorType=RuntimeException"));
            assertFalse(messages.contains("sensitive customer detail"));
            assertFalse(messages.contains("ticketId=100"));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private ServiceTicket ticket(Long id, Long companyId, Long technicianId) {
        return ServiceTicket.builder()
                .id(id)
                .companyId(companyId)
                .customerId(200L)
                .assignedTechnicianId(technicianId)
                .status(ServiceTicket.TicketStatus.COMPLETED)
                .build();
    }

    private void authenticate(Long id, Long companyId, String role) {
        User user = new User();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setRole(role);
        user.setUsername("test-user");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
