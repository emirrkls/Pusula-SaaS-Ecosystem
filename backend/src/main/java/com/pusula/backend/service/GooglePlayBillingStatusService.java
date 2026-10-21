package com.pusula.backend.service;

import com.pusula.backend.entity.PlanType;
import com.pusula.backend.repository.CompanyRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.time.*;

@Service
public class GooglePlayBillingStatusService {
    private final CompanyRepository companies;
    private final GooglePlayReceiptStore receipts;
    private final GooglePlayAccessTokenProvider credentials;
    private final String packageName;
    public GooglePlayBillingStatusService(CompanyRepository companies, GooglePlayReceiptStore receipts,
            GooglePlayAccessTokenProvider credentials, @Value("${google.play.package-name:}") String packageName) {
        this.companies=companies; this.receipts=receipts; this.credentials=credentials; this.packageName=packageName;
    }
    public Status status(Long companyId) {
        var company=companies.findById(companyId).orElseThrow(() -> new IllegalArgumentException("Company not found"));
        boolean ready=receipts.ready() && credentials.configured() && "com.pusula.service".equals(packageName);
        String state=company.getSubscriptionStatus();
        boolean restricted="SUSPENDED".equals(state) || (Boolean.TRUE.equals(company.getIsReadOnly())
                && !"EXPIRED".equals(state) && !"REVOKED".equals(state) && !"PAYMENT_FAILED".equals(state));
        boolean otherProvider=company.getPlanType()!=PlanType.CIRAK && company.getSubscriptionProvider()!=null
                && !"GOOGLE_PLAY".equals(company.getSubscriptionProvider()) && !"TRIAL".equals(state)
                && (company.getSubscriptionExpiresAt()==null || company.getSubscriptionExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
        String current=company.getExternalSubscriptionId();
        var receipt=ready && "GOOGLE_PLAY".equals(company.getSubscriptionProvider()) && current!=null && current.startsWith("google:")
                ? receipts.find(current.substring(7)).filter(row -> companyId.equals(row.getCompanyId()) && !row.isRetired()).orElse(null) : null;
        return new Status(ready, ready && !restricted && !otherProvider,
                !ready ? "server_not_ready" : restricted ? "account_restricted" : otherProvider ? "another_provider_active" : null,
                company.getPlanType().name(), receipt==null ? null : receipt.getId(),
                receipt==null ? null : receipt.getBasePlanId(), receipt==null || receipt.getDeferredProductId()==null
                    ? null : receipt.getDeferredProductId().toUpperCase(java.util.Locale.ROOT));
    }
    public record Status(boolean ready, boolean purchaseAllowed, String reason, String plan,
                         String receiptHash, String basePlanId, String scheduledPlan) {}
}
