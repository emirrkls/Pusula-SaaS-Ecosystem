package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import com.pusula.backend.dto.WhatsAppOnboardingDtos;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.net.http.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WhatsAppCoexistenceOnboardingTest {
    @Mock WhatsAppOnboardingSessionRepository sessions;
    @Mock WhatsAppBusinessIntegrationRepository integrations;
    @Mock WhatsAppCredentialCrypto crypto;
    @Mock WhatsAppCoexistenceSyncStore sync;
    @Mock HttpClient http;
    WhatsAppOnboardingService service;
    WhatsAppOnboardingSession session;
    final String state = "a".repeat(43);

    @BeforeEach void setup() {
        var properties = new WhatsAppIntegrationProperties("12345", "secret", "67890",
            "https://example.test/whatsapp-connect", "key", "v26.0", "verify");
        service = new WhatsAppOnboardingService(properties, sessions, integrations, crypto, new ObjectMapper(), sync, http);
        session = new WhatsAppOnboardingSession();
        session.setCompanyId(10L); session.setUserId(7L); session.setExpiresAt(LocalDateTime.now().plusMinutes(15));
    }
    @SuppressWarnings("unchecked")
    HttpResponse<String> response(String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200); when(response.body()).thenReturn(body); return response;
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    void meta(String phones) throws Exception {
        var tokenResponse = response("{\"access_token\":\"test-only-token\",\"expires_in\":3600}");
        var phoneResponse = response(phones);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
            .thenReturn(tokenResponse, phoneResponse);
        when(sessions.findByStateHash(anyString())).thenReturn(Optional.of(session));
    }
    WhatsAppOnboardingDtos.CompleteRequest request(String phone) {
        return new WhatsAppOnboardingDtos.CompleteRequest(state, "test-code", "12345", phone, true);
    }

    @Test void resolvesSingleVerifiedCoexistencePhoneAndQueuesSyncWithoutRegisteringNumber() throws Exception {
        meta("{\"data\":[{\"id\":\"67890\",\"display_phone_number\":\"905550000000\",\"verified_name\":\"Demo\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\"}]}");
        when(crypto.encrypt("test-only-token")).thenReturn("ciphertext");
        doAnswer(invocation -> {
            WhatsAppBusinessIntegration saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 22L);
            return saved;
        }).when(integrations).saveAndFlush(any());
        assertTrue(service.complete(request(null)).connected());
        ArgumentCaptor<WhatsAppBusinessIntegration> capture = ArgumentCaptor.forClass(WhatsAppBusinessIntegration.class);
        verify(integrations).saveAndFlush(capture.capture());
        var integration = capture.getValue();
        assertTrue(integration.isCoexistence()); assertEquals("67890", integration.getPhoneNumberId());
        assertEquals("ciphertext", integration.getAccessTokenCiphertext());
        assertEquals(22L, session.getCompletedIntegrationId());
        assertEquals(integration.getCoexistenceGeneration(), session.getCompletedGeneration());
        verify(sync).enqueue(eq(integration), any());
        ArgumentCaptor<HttpRequest> requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(2)).send(requests.capture(), any(HttpResponse.BodyHandler.class));
        assertTrue(requests.getAllValues().stream().noneMatch(r -> r.uri().getPath().endsWith("/register")));
    }
    @Test void neverGuessesAmongMultipleEligiblePhones() throws Exception {
        meta("{\"data\":[{\"id\":\"67890\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\"},{\"id\":\"67891\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\"}]}");
        assertThrows(IllegalStateException.class, () -> service.complete(request(null)));
        verify(integrations, never()).saveAndFlush(any()); verifyNoInteractions(sync, crypto);
    }
    @Test void doesNotInferUniquePhoneFromTruncatedPage() throws Exception {
        meta("{\"data\":[{\"id\":\"67890\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\"}],\"paging\":{\"next\":\"https://example.test/page2\"}}");
        assertThrows(IllegalStateException.class, () -> service.complete(request(null)));
        verifyNoInteractions(sync, crypto);
    }
    @Test void doesNotTrustBrowserCoexistenceFlagOrForeignPhone() throws Exception {
        meta("{\"data\":[{\"id\":\"67890\",\"is_on_biz_app\":false,\"platform_type\":\"CLOUD_API\"}]}");
        assertThrows(IllegalStateException.class, () -> service.complete(request("67890")));
        verifyNoInteractions(sync, crypto);
    }
    @Test void refusesPhoneAlreadyAssignedToAnotherTenant() throws Exception {
        meta("{\"data\":[{\"id\":\"67890\",\"is_on_biz_app\":true,\"platform_type\":\"CLOUD_API\"}]}");
        var foreign = new WhatsAppBusinessIntegration(); foreign.setCompanyId(99L);
        when(integrations.findByPhoneNumberIdAndDeletedFalse("67890")).thenReturn(Optional.of(foreign));
        assertThrows(IllegalStateException.class, () -> service.complete(request("67890")));
        verifyNoInteractions(sync, crypto);
    }
    @Test void repeatedCompletionReturnsRecordedResultWithoutSecondCodeExchange() {
        var integration = completed();
        when(sessions.findByStateHash(anyString())).thenReturn(Optional.of(session));
        when(integrations.findByCompanyIdAndDeletedFalse(10L)).thenReturn(Optional.of(integration));
        assertTrue(service.complete(request(null)).connected());
        assertTrue(service.complete(request("67890")).connected());
        verifyNoInteractions(http, crypto);
        verify(sync, never()).enqueue(any(), any());
    }
    @Test void usedSessionCannotBeReboundToDifferentAssetOrGeneration() {
        var integration = completed();
        integration.setCoexistenceGeneration("new-generation");
        when(sessions.findByStateHash(anyString())).thenReturn(Optional.of(session));
        when(integrations.findByCompanyIdAndDeletedFalse(10L)).thenReturn(Optional.of(integration));
        assertThrows(IllegalArgumentException.class, () -> service.progress(state));
        verifyNoInteractions(http, crypto, sync);
    }
    @Test void progressRequiresCompletedSessionAndHas24HourLimit() {
        when(sessions.findByStateHash(anyString())).thenReturn(Optional.of(session));
        assertThrows(IllegalArgumentException.class, () -> service.progress(state));
        completed(); session.setUsedAt(LocalDateTime.now().minusHours(25));
        assertThrows(IllegalArgumentException.class, () -> service.progress(state));
        verifyNoInteractions(integrations, http, sync);
    }
    WhatsAppBusinessIntegration completed() {
        var integration = new WhatsAppBusinessIntegration();
        ReflectionTestUtils.setField(integration, "id", 22L);
        integration.setCompanyId(10L); integration.setWabaId("12345"); integration.setPhoneNumberId("67890");
        integration.setCoexistenceGeneration("generation"); integration.setStatus("CONNECTED");
        session.setUsedAt(LocalDateTime.now()); session.setCompletedIntegrationId(22L); session.setCompletedGeneration("generation");
        return integration;
    }
}
