package com.pusula.backend.controller;

import com.pusula.backend.config.GlobalExceptionHandler;
import com.pusula.backend.service.PushDeviceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PushDeviceControllerTest {
    private PushDeviceService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(PushDeviceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PushDeviceController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void registerAndUnregisterReturnNoContent() throws Exception {
        mvc.perform(post("/api/push-devices/register").contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/push-devices/unregister").contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isNoContent());
        verify(service).register(any());
        verify(service).unregister(any());
    }

    @Test
    void rejectsBlankTooLongAndNonHexTokens() throws Exception {
        assertBadRequest(jsonWithToken(""));
        assertBadRequest(jsonWithToken("a".repeat(201)));
        assertBadRequest(jsonWithToken("not-hex"));
    }

    @Test
    void rejectsInvalidPlatformEnvironmentAndMissingBundle() throws Exception {
        assertBadRequest(validJson().replace("\"IOS\"", "\"ANDROID\""));
        assertBadRequest(validJson().replace("\"SANDBOX\"", "\"STAGING\""));
        assertBadRequest(validJson().replace("com.pusula.service", ""));
    }

    private void assertBadRequest(String body) throws Exception {
        mvc.perform(post("/api/push-devices/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    private String validJson() { return jsonWithToken("a1".repeat(32)); }

    private String jsonWithToken(String token) {
        return "{\"token\":\"" + token + "\",\"platform\":\"IOS\","
                + "\"environment\":\"SANDBOX\",\"bundleId\":\"com.pusula.service\"}";
    }
}
