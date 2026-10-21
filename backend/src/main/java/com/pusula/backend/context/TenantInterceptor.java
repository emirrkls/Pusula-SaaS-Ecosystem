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
                        && !isOwnSupportRequest(request.getRequestURI())
                        && !request.getRequestURI().startsWith("/api/payment/")
                        && companyRepository.findById(user.getCompanyId())
                                .map(company -> Boolean.TRUE.equals(company.getIsReadOnly())
                                        && !isGoogleReactivationRequest(request, user, company))
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

    // Expired Google customers must be able to verify a new, independently validated purchase.
    // This only reaches the existing admin/ownership/Google verification checks; it grants no access.
    private boolean isGoogleReactivationRequest(HttpServletRequest request, User user,
            com.pusula.backend.entity.Company company) {
        String state = company.getSubscriptionStatus();
        return "POST".equals(request.getMethod())
                && "/api/subscription/google-verify".equals(request.getRequestURI())
                && "COMPANY_ADMIN".equals(user.getRole())
                && "GOOGLE_PLAY".equals(company.getSubscriptionProvider())
                && ("EXPIRED".equals(state) || "REVOKED".equals(state) || "PAYMENT_FAILED".equals(state));
    }

    // The support service independently checks the actual actor, ownership and role.
    // Never exempt admin/impersonation requests or lookalike paths from normal business protections.
    private boolean isOwnSupportRequest(String uri) {
        return uri.equals("/api/support/tickets") || uri.matches("/api/support/tickets/[0-9]+/(messages|read)");
    }
}
