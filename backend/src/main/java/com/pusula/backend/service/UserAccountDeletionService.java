package com.pusula.backend.service;

import com.pusula.backend.entity.SignatureErasureTask;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.UUID;

@Service
public class UserAccountDeletionService {
    private final UserRepository users;
    private final SocialAccountDeletionService social;
    private final PushDeviceRepository devices;
    private final AuditLogRepository audits;
    private final SignatureErasureTaskRepository signatures;
    private final PasswordEncoder passwords;
    private final CompanyRepository companies;

    public UserAccountDeletionService(UserRepository users, SocialAccountDeletionService social,
            PushDeviceRepository devices, AuditLogRepository audits, SignatureErasureTaskRepository signatures,
            PasswordEncoder passwords, CompanyRepository companies) {
        this.users = users; this.social = social; this.devices = devices; this.audits = audits;
        this.signatures = signatures; this.passwords = passwords; this.companies = companies;
    }

    @Transactional
    public void delete(User principal) {
        User user = users.lockByIdAndCompanyId(principal.getId(), principal.getCompanyId()).orElseThrow();
        // Apple revocation failure must not report a successful deletion or remove its binding.
        social.revokeAndRemove(user.getId());
        devices.deleteByCompanyIdAndUserId(user.getCompanyId(), user.getId());
        audits.deletePersonalAuthenticationHistory(user.getId());
        audits.anonymizeActor(user.getId());
        audits.eraseUserProfileSnapshots(user.getId());
        if (user.getSignaturePath() != null && !user.getSignaturePath().isBlank()) {
            signatures.save(new SignatureErasureTask(user.getId(), user.getSignaturePath()));
        }
        String formerUsername = user.getUsername();
        String formerName = user.getFullName();
        user.setUsername("deleted-" + UUID.randomUUID());
        user.setPasswordHash(passwords.encode(UUID.randomUUID().toString()));
        user.setFullName("Silinen kullanıcı");
        user.setSignaturePath(null);
        user.setLocalPasswordEnabled(false);
        user.setMobileOnboardingVersion(0);
        // A non-personal tombstone preserves historical business foreign keys, not a login account.
        user.setDeleted(true);
        users.saveAndFlush(user);
        if (user.getCompanyId() != null && users.findByCompanyId(user.getCompanyId()).isEmpty()) {
            companies.lockById(user.getCompanyId()).ifPresent(company -> {
                // Remove personal registration defaults only; do not erase shared business records.
                if (formerUsername.equalsIgnoreCase(String.valueOf(company.getEmail()))) company.setEmail(null);
                if (formerUsername.equalsIgnoreCase(String.valueOf(company.getBillingEmail()))) company.setBillingEmail(null);
                if (formerName != null && (formerName + " Servisi").equals(company.getName())) {
                    company.setName("Hesabı silinen işletme");
                    company.setEmail(null);
                    company.setBillingEmail(null);
                }
                companies.save(company);
            });
        }
    }
}
