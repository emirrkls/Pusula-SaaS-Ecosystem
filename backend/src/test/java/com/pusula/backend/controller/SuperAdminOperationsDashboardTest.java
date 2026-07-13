package com.pusula.backend.controller;

import com.pusula.backend.entity.AuditLog;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.WebhookEvent;
import com.pusula.backend.entity.WebhookEventStatus;
import com.pusula.backend.repository.AuditLogRepository;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.PaymentEventRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.repository.WebhookEventRepository;
import com.pusula.backend.service.AuditLogService;
import com.pusula.backend.service.FeatureService;
import com.pusula.backend.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminOperationsDashboardTest {

    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private FeatureService featureService;
    @Mock
    private JwtService jwtService;
    @Mock
    private PaymentEventRepository paymentEventRepository;
    @Mock
    private WebhookEventRepository webhookEventRepository;

    private SuperAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new SuperAdminController(
                companyRepository,
                auditLogRepository,
                userRepository,
                passwordEncoder,
                auditLogService,
                featureService,
                jwtService,
                paymentEventRepository,
                webhookEventRepository);
    }

    @Test
    void operationsDashboard_returnsExpectedShape() {
        when(auditLogRepository.countAuthFailuresGroupedByDateSince(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(webhookEventRepository.countByStatusAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(WebhookEventStatus.FAILED),
                org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(companyRepository.countByIsReadOnlyTrue()).thenReturn(0L);
        when(auditLogRepository.countAuthFailuresSince(org.mockito.ArgumentMatchers.any())).thenReturn(0L);

        ResponseEntity<Map<String, Object>> response = controller.getOperationsDashboard();
        Map<String, Object> body = response.getBody();

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(body);
        assertTrue(body.containsKey("deployVersion"));
        assertTrue(body.containsKey("webhookFailedCount24h"));
        assertTrue(body.containsKey("authFailureTrend"));
        assertTrue(body.containsKey("tenantReadOnlyCount"));
        assertTrue(body.containsKey("alerts"));
    }

    @Test
    void operationsDashboard_whenThresholdsExceeded_generatesAlerts() {
        when(auditLogRepository.countAuthFailuresGroupedByDateSince(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(webhookEventRepository.countByStatusAndCreatedAtAfter(
                org.mockito.ArgumentMatchers.eq(WebhookEventStatus.FAILED),
                org.mockito.ArgumentMatchers.any())).thenReturn(5L);
        when(companyRepository.countByIsReadOnlyTrue()).thenReturn(1L);
        when(auditLogRepository.countAuthFailuresSince(org.mockito.ArgumentMatchers.any())).thenReturn(20L);

        ResponseEntity<Map<String, Object>> response = controller.getOperationsDashboard();
        Map<String, Object> body = response.getBody();
        List<?> alerts = (List<?>) body.get("alerts");

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(alerts);
        assertTrue(alerts.size() >= 2);
    }
}
