package com.pusula.backend.controller;

import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.service.ReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportControllerTechnicianPerformanceTest {

    @Mock
    private ServiceTicketRepository ticketRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportService reportService;

    private ReportController controller;

    @BeforeEach
    void setUp() {
        controller = new ReportController(ticketRepository, userRepository, reportService);

        User admin = User.builder()
                .id(1L)
                .companyId(42L)
                .username("admin")
                .passwordHash("secret")
                .role("COMPANY_ADMIN")
                .fullName("Company Admin")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, admin.getAuthorities()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void technicianPerformance_usesCurrentCompanyScope() {
        LocalDateTime completedAt = LocalDateTime.now().minusDays(1);
        ServiceTicket ticket = ServiceTicket.builder()
                .id(100L)
                .companyId(42L)
                .customerId(200L)
                .assignedTechnicianId(10L)
                .status(ServiceTicket.TicketStatus.COMPLETED)
                .build();
        ticket.setUpdatedAt(completedAt);

        User technician = User.builder()
                .id(10L)
                .companyId(42L)
                .username("tech")
                .passwordHash("secret")
                .role("TECHNICIAN")
                .fullName("Ali Usta")
                .build();

        when(ticketRepository.findCompletedTicketsSince(eq(42L), any(LocalDateTime.class)))
                .thenReturn(List.of(ticket));
        when(userRepository.findByIdAndCompanyId(10L, 42L)).thenReturn(Optional.of(technician));

        ResponseEntity<Map<String, Map<String, Integer>>> response = controller.getTechnicianPerformance();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        String dateKey = completedAt.toLocalDate().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        assertEquals(1, response.getBody().get("Ali Usta").get(dateKey));
        verify(ticketRepository).findCompletedTicketsSince(eq(42L), any(LocalDateTime.class));
        verify(userRepository).findByIdAndCompanyId(10L, 42L);
    }
}
