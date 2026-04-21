package com.pusula.backend.aspect;

import com.pusula.backend.annotation.CheckQuota;
import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.context.TenantContext;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.service.FeatureService;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * AOP aspect that intercepts methods annotated with @RequiresFeature or @CheckQuota.
 * Uses TenantContext (or falls back to SecurityContext) to determine the tenant
 * and delegates to FeatureService for enforcement.
 *
 * Also enforces READ-ONLY mode: if a company's subscription has expired,
 * all write operations (create/update/delete) annotated with @RequiresFeature are blocked.
 */
@Aspect
@Component
public class FeatureGateAspect {

    private static final Logger log = LoggerFactory.getLogger(FeatureGateAspect.class);

    private final FeatureService featureService;
    private final CompanyRepository companyRepository;

    public FeatureGateAspect(FeatureService featureService, CompanyRepository companyRepository) {
        this.featureService = featureService;
        this.companyRepository = companyRepository;
    }

    @Before("@annotation(requiresFeature)")
    public void checkFeature(RequiresFeature requiresFeature) {
        Long companyId = resolveCompanyId();
        if (companyId != null) {
            // Check read-only mode first
            checkReadOnlyMode(companyId);
            // Then check feature flag
            featureService.checkFeature(companyId, requiresFeature.value());
        }
    }

    @Before("@annotation(checkQuota)")
    public void checkQuota(CheckQuota checkQuota) {
        Long companyId = resolveCompanyId();
        if (companyId != null) {
            checkReadOnlyMode(companyId);
            featureService.checkQuota(companyId, checkQuota.value());
        }
    }

    /**
     * Enforce read-only mode for expired subscriptions.
     * Throws RuntimeException to block write operations.
     */
    private void checkReadOnlyMode(Long companyId) {
        Company company = companyRepository.findById(companyId).orElse(null);
        if (company != null && Boolean.TRUE.equals(company.getIsReadOnly())) {
            log.warn("Write operation blocked for company {} (READ_ONLY mode)", companyId);
            throw new RuntimeException(
                    "Aboneliğiniz sona ermiştir. Yeni veri girişi yapılamaz. Lütfen paketinizi yenileyin.");
        }
    }

    private Long resolveCompanyId() {
        // Prefer TenantContext (set by TenantInterceptor)
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) return tenantId;

        // Fallback to SecurityContext
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof User user) {
                return user.getCompanyId();
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
}
