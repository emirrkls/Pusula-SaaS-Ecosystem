package com.pusula.backend.context;

import com.pusula.backend.entity.User;
import com.pusula.backend.service.JwtService;
import com.pusula.backend.repository.CompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Sets TenantContext from the authenticated user's companyId on each request.
 * Must run after Spring Security authentication is complete.
 * 
 * Ensures TenantContext.clear() is ALWAYS called in afterCompletion
 * to prevent thread-local memory leaks in pooled thread environments.
 */
@Component
public class TenantInterceptor implements HandlerInterceptor {

    private final JwtService jwtService;
    private final CompanyRepository companyRepository;

    public TenantInterceptor(JwtService jwtService, CompanyRepository companyRepository) {
        this.jwtService = jwtService;
        this.companyRepository = companyRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof User user) {
            if (isSuperAdmin(user)) {
                Long impersonatedCompanyId = extractCompanyIdFromJwt(request);
                boolean readOnlyImpersonation = extractReadOnlyImpersonationFromJwt(request);
                if (impersonatedCompanyId != null) {
                    TenantContext.setTenantId(impersonatedCompanyId);
                    TenantContext.setReadOnlyImpersonation(readOnlyImpersonation);
                    if (readOnlyImpersonation && isMutationMethod(request.getMethod())) {
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        return false;
                    }
                } else {
                    TenantContext.clear();
                }
            } else {
                TenantContext.setTenantId(user.getCompanyId());
                TenantContext.setReadOnlyImpersonation(false);
                if (isMutationMethod(request.getMethod())
                        && !request.getRequestURI().startsWith("/api/payment/")
                        && companyRepository.findById(user.getCompanyId())
                                .map(company -> Boolean.TRUE.equals(company.getIsReadOnly()))
                                .orElse(false)) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    try {
                        response.getWriter().write("{\"status\":403,\"code\":\"COMPANY_READ_ONLY\","
                                + "\"message\":\"Abonelik salt okunur durumda; veri değiştirilemez.\"}");
                    } catch (java.io.IOException ignored) {
                        // The response status still blocks the mutation.
                    }
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                 Object handler, Exception ex) {
        TenantContext.clear();
    }

    private boolean isSuperAdmin(User user) {
        if (user == null || user.getRole() == null) {
            return false;
        }
        String role = user.getRole().trim().toUpperCase();
        return role.startsWith("SUPER_ADMIN");
    }

    private Long extractCompanyIdFromJwt(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token == null) {
            return null;
        }
        try {
            return jwtService.extractCompanyId(token);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean extractReadOnlyImpersonationFromJwt(HttpServletRequest request) {
        String token = extractBearerToken(request);
        if (token == null) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(jwtService.extractBooleanClaim(token, "impersonationReadOnly"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }
        return authHeader.substring(7);
    }

    private boolean isMutationMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method);
    }
}
