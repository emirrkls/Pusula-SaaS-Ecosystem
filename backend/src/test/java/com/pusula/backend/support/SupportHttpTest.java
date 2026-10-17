package com.pusula.backend.support;

import org.junit.jupiter.api.*;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.http.MediaType;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SupportHttpTest {
    private final SupportService service=mock(SupportService.class);
    private final MockMvc http=MockMvcBuilders.standaloneSetup(new SupportController(service)).setControllerAdvice(new SupportErrors(),new SupportBodyAdvice()).build();
    @Test void responseIsPrivateAndForbiddenDoesNotExposePrincipalOrExceptionText() throws Exception {
        when(service.unread(false)).thenThrow(new org.springframework.security.access.AccessDeniedException("sensitive-principal-detail"));
        http.perform(get("/api/support/unread-count")).andExpect(status().isForbidden()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.code").value("SUPPORT_FORBIDDEN")).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-principal"))));
    }
    @Test void uncertainBackendFailureCannotBeMisclassifiedAsEditableBadRequest() throws Exception {
        when(service.unread(false)).thenThrow(new org.springframework.dao.DataIntegrityViolationException("SQL private payload"));
        http.perform(get("/api/support/unread-count")).andExpect(status().isServiceUnavailable()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SQL"))));
        doThrow(new IllegalStateException("private-detail")).when(service).unread(false);
        http.perform(get("/api/support/unread-count")).andExpect(status().isServiceUnavailable());
    }
    @Test void invalidJsonIsRejectedWithoutEchoingInput() throws Exception {
        http.perform(post("/api/support/tickets").contentType(MediaType.APPLICATION_JSON).content("{\"secret\":\"sensitive-value\"" )).andExpect(status().isBadRequest()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("sensitive-value"))));
        verifyNoInteractions(service);
    }
    @Test void declaredOversizeAndUnknownLengthStreamsAreBothBounded() throws Exception {
        var advice=new SupportBodyAdvice();var declared=new MockHttpInputMessage(new byte[]{1});declared.getHeaders().setContentLength(SupportBodyAdvice.MAX_BODY+1);
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->advice.beforeBodyRead(declared,null,null,null));
        var chunked=new MockHttpInputMessage(new byte[(int)SupportBodyAdvice.MAX_BODY+1]);var input=advice.beforeBodyRead(chunked,null,null,null);
        assertThrows(IOException.class,()->input.getBody().transferTo(OutputStream.nullOutputStream()));
    }
}
