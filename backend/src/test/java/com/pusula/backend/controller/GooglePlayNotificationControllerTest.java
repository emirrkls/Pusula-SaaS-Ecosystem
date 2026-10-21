package com.pusula.backend.controller;

import com.pusula.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GooglePlayNotificationControllerTest {
    final GooglePlayPushAuthenticator auth = mock(GooglePlayPushAuthenticator.class);
    final GooglePlayNotificationService service = mock(GooglePlayNotificationService.class);
    MockMvc mvc;
    final String path = "/api/public/google/play-notifications";
    @BeforeEach void setup() { mvc = MockMvcBuilders.standaloneSetup(new GooglePlayNotificationController(auth, service)).build(); }
    @Test void authenticatesBeforeAcceptingPayloadAndReturnsNoContent() throws Exception {
        mvc.perform(post(path).header("Authorization", "Bearer oidc").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        var order = inOrder(auth, service); order.verify(auth).authenticate("Bearer oidc"); order.verify(service).receive("{}".getBytes());
    }
    @Test void unauthenticatedPushHasNoPersistence() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED)).when(auth).authenticate(null);
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void boundedBodyRejectsOversize() throws Exception {
        mvc.perform(post(path).header("Authorization", "Bearer oidc").contentType(MediaType.APPLICATION_JSON).content(new byte[32769]))
                .andExpect(status().isPayloadTooLarge()); verifyNoInteractions(service);
    }
    @Test void persistenceFailureIsNotAcknowledged() throws Exception {
        doThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)).when(service).receive(any());
        mvc.perform(post(path).header("Authorization", "Bearer oidc").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isServiceUnavailable());
    }
}
