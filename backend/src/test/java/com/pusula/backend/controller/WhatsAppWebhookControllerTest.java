package com.pusula.backend.controller;

import com.pusula.backend.service.WhatsAppWebhookSecurity;
import com.pusula.backend.service.WhatsAppWebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class WhatsAppWebhookControllerTest {
    @Mock WhatsAppWebhookSecurity security;
    @Mock WhatsAppWebhookService service;

    @Test
    void returnsChallengeOnlyForValidVerificationToken() {
        WhatsAppWebhookController controller = new WhatsAppWebhookController(security, service);
        when(security.isVerificationRequestValid("subscribe", "token")).thenReturn(true);

        assertEquals(200, controller.verify("subscribe", "token", "challenge").getStatusCode().value());
        assertEquals("challenge", controller.verify("subscribe", "token", "challenge").getBody());
        assertEquals(403, controller.verify("subscribe", "wrong", "challenge").getStatusCode().value());
    }

    @Test
    void invalidVerificationTokenReturnsForbiddenForJsonAcceptHeader() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new WhatsAppWebhookController(security, service)).build();

        mockMvc.perform(get("/api/public/whatsapp/webhook")
                        .accept(MediaType.APPLICATION_JSON)
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "challenge"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsUnsignedPayloadAndProcessesValidPayload() {
        WhatsAppWebhookController controller = new WhatsAppWebhookController(security, service);
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        when(security.isPayloadSignatureValid(body, "bad")).thenReturn(false);
        when(security.isPayloadSignatureValid(body, "good")).thenReturn(true);

        assertEquals(401, controller.receive(body, "bad").getStatusCode().value());
        assertEquals(200, controller.receive(body, "good").getStatusCode().value());
        verify(service).processStatusEvents("{}");
    }
}
