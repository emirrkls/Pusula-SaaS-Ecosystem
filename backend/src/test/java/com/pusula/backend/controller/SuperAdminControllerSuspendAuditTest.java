package com.pusula.backend.controller;

import com.pusula.backend.entity.Company;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerSuspendAuditTest {

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
    void toggleSuspend_suspendThenRevert_writesAuditLogs() {
        Company company = new Company();
        company.setId(10L);
        company.setSubscriptionStatus("ACTIVE");
        company.setIsReadOnly(false);

        when(companyRepository.findById(10L)).thenReturn(Optional.of(company));
        when(companyRepository.save(any(Company.class))).thenAnswer(inv -> inv.getArgument(0));

        SuperAdminController.SuspendActionRequest request = new SuperAdminController.SuspendActionRequest();
        request.reason = "Operasyonel inceleme";
        request.confirmAction = true;

        ResponseEntity<Company> first = controller.toggleSuspendCompany(10L, request);
        assertEquals(200, first.getStatusCode().value());
        assertEquals("SUSPENDED", first.getBody().getSubscriptionStatus());

        ResponseEntity<Company> second = controller.toggleSuspendCompany(10L, request);
        assertEquals(200, second.getStatusCode().value());
        assertEquals("ACTIVE", second.getBody().getSubscriptionStatus());

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogService, times(2)).logChange(
                actionCaptor.capture(), any(), any(), any(), any(), any());
        assertEquals("UPDATE", actionCaptor.getAllValues().get(0));
        assertEquals("UPDATE", actionCaptor.getAllValues().get(1));
    }
}
