package com.pusula.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.pusula.backend.service.UploadUrlSigner;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class UploadSecurityFilterThumbnailTest {

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsUnsignedThumbnailRequest() throws Exception {
        UploadUrlSigner signer = mock(UploadUrlSigner.class);
        UploadSecurityFilter filter = new UploadSecurityFilter(signer);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/uploads/service-photo-thumbnails/1/75/photo.jpg.thumb.jpg");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        verify(chain, never()).doFilter(any(), any());
    }

    @Test
    void acceptsValidSignedThumbnailRequest() throws Exception {
        UploadUrlSigner signer = mock(UploadUrlSigner.class);
        when(signer.isValid("/uploads/service-photo-thumbnails/1/75/photo.jpg.thumb.jpg", "123", "abc"))
                .thenReturn(true);
        UploadSecurityFilter filter = new UploadSecurityFilter(signer);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET", "/uploads/service-photo-thumbnails/1/75/photo.jpg.thumb.jpg");
        request.setParameter("expires", "123");
        request.setParameter("sig", "abc");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
