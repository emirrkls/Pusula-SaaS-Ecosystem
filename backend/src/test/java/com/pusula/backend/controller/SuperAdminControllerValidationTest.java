package com.pusula.backend.controller;

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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ExtendWith(MockitoExtension.class)
class SuperAdminControllerValidationTest {

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
    void createCompany_missingReason_returnsReasonRequired() {
        SuperAdminController.CreateCompanyRequest request = new SuperAdminController.CreateCompanyRequest();
        request.name = "Test Company";
        request.adminUsername = "admin@test.com";
        request.adminPassword = "12345678";
        request.reason = "bad";
        request.confirmAction = true;

        ResponseEntity<?> response = controller.createCompany(request);
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(body);
        assertEquals("REASON_REQUIRED", body.get("code"));
    }

    @Test
    void createCompany_missingConfirmation_returnsConfirmationRequired() {
        SuperAdminController.CreateCompanyRequest request = new SuperAdminController.CreateCompanyRequest();
        request.name = "Test Company";
        request.adminUsername = "admin@test.com";
        request.adminPassword = "12345678";
        request.reason = "valid reason";
        request.confirmAction = false;

        ResponseEntity<?> response = controller.createCompany(request);
        Map<?, ?> body = (Map<?, ?>) response.getBody();

        assertEquals(400, response.getStatusCode().value());
        assertNotNull(body);
        assertEquals("CONFIRMATION_REQUIRED", body.get("code"));
    }
}
