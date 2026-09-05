package com.pusula.backend.config;

import com.pusula.backend.service.UploadUrlSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class UploadSecurityFilter extends OncePerRequestFilter {
    private final UploadUrlSigner signer;

    public UploadSecurityFilter(UploadUrlSigner signer) {
        this.signer = signer;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/uploads/service-photos/")
                || path.startsWith("/uploads/service-photo-thumbnails/")
                || path.startsWith("/uploads/signatures/"));
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean authenticated = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal());
        boolean signed = signer.isValid(request.getRequestURI(),
                request.getParameter("expires"), request.getParameter("sig"));
        if (!authenticated && !signed) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"UPLOAD_ACCESS_DENIED\","
                    + "\"message\":\"Dosya bağlantısı geçersiz veya süresi dolmuş.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
