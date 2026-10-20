package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class WhatsAppCoexistenceApiClientTest {
    HttpClient http = mock(HttpClient.class);
    WhatsAppCloudApiClient client = new WhatsAppCloudApiClient(
        new WhatsAppIntegrationProperties("a","s","c","url","key","v26.0","verify"), new ObjectMapper(), http);
    @SuppressWarnings({"unchecked","rawtypes"})
    void response(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        if (status / 100 == 2) when(response.body()).thenReturn(body);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);
    }
    @Test void usesPostSmbAppDataAndRequiresRequestId() throws Exception {
        response(200, "{\"request_id\":\"req-test\"}");
        assertEquals("req-test", client.requestAppData("12345", "test-only-token", "history"));
        ArgumentCaptor<HttpRequest> capture = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http).send(capture.capture(), any(HttpResponse.BodyHandler.class));
        var request = capture.getValue();
        assertEquals("POST", request.method()); assertTrue(request.uri().getPath().endsWith("/12345/smb_app_data"));
        assertFalse(request.uri().toString().contains("test-only-token"));
    }
    @Test void acceptedResponseWithoutRequestIdIsUnknownNotSuccess() throws Exception {
        response(200, "{\"success\":true}");
        var error = assertThrows(WhatsAppCloudApiClient.SyncRequestException.class,
            () -> client.requestAppData("12345", "token", "history"));
        assertEquals("REQUEST_OUTCOME_UNKNOWN", error.code()); assertFalse(error.definitelyRejectedRetryable());
    }
    @Test void rateLimitIsDefiniteRejectionButServerErrorIsAmbiguous() throws Exception {
        response(429, "");
        assertTrue(assertThrows(WhatsAppCloudApiClient.SyncRequestException.class,
            () -> client.requestAppData("12345", "token", "history")).definitelyRejectedRetryable());
        response(503, "");
        assertFalse(assertThrows(WhatsAppCloudApiClient.SyncRequestException.class,
            () -> client.requestAppData("12345", "token", "history")).definitelyRejectedRetryable());
    }
    @Test void connectionLossDoesNotLeakExceptionOrRetryOneShotRequest() throws Exception {
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenThrow(new IOException("SECRET"));
        var error = assertThrows(WhatsAppCloudApiClient.SyncRequestException.class,
            () -> client.requestAppData("12345", "token", "smb_app_state_sync"));
        assertFalse(error.getMessage().contains("SECRET")); assertFalse(error.definitelyRejectedRetryable());
        verify(http).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
