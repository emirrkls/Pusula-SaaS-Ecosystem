package com.pusula.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.repository.WebhookEventRepository;
import com.pusula.backend.service.AuditLogService;
import com.pusula.backend.service.SubscriptionService;
import com.pusula.backend.service.WebhookSecurityService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerWebhookSecurityTest {

    @Mock
    private SubscriptionService subscriptionService;
    @Mock
    private WebhookSecurityService webhookSecurityService;
    @Mock
    private AuditLogService auditLogService;
    @Mock
    private WebhookEventRepository webhookEventRepository;
    @Mock
    private HttpServletRequest request;

    private PaymentController paymentController;

    @BeforeEach
    void setUp() {
        paymentController = new PaymentController(
                subscriptionService,
                webhookSecurityService,
                auditLogService,
                webhookEventRepository,
                new ObjectMapper());
    }

    @Test
    void iyzicoWebhook_invalidSignature_returns401AndAudits() {
        String rawBody = "{\"iyziEventType\":\"subscription.order.success\",\"subscriptionReferenceCode\":\"sub-1\"}";
        when(webhookSecurityService.isIyzicoSignatureValid(any(), any())).thenReturn(false);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getRequestURI()).thenReturn("/api/payment/webhook/iyzico");
        when(webhookEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ResponseEntity<Map<String, String>> response = paymentController.iyzicoWebhook(
                rawBody,
                "bad-signature",
                null,
                request);

        assertEquals(401, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("INVALID_WEBHOOK_SIGNATURE", response.getBody().get("code"));
        verify(auditLogService, times(1)).logAuth(any(), any(), any(), eq("WEBHOOK_SIGNATURE_INVALID"), any(), any());
    }
}
