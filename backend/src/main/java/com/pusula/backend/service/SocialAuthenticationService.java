package com.pusula.backend.service;

import com.pusula.backend.dto.*;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;

@Service
public class SocialAuthenticationService {
    private final GoogleIdentityTokenVerifier google;
    private final AppleIdentityTokenVerifier apple;
    private final AppleAuthChallengeService challenges;
    private final AppleSignInOAuthClient appleOAuth;
    private final SocialAuthIdentityRepository identities;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final AuthenticationService authentication;
    private final AuditLogService audit;
    private final AuditLogRepository auditLogs;

    public SocialAuthenticationService(GoogleIdentityTokenVerifier google, AppleIdentityTokenVerifier apple,
            AppleAuthChallengeService challenges, AppleSignInOAuthClient appleOAuth, SocialAuthIdentityRepository identities,
            UserRepository users, CompanyRepository companies, PasswordEncoder passwords, JwtService jwt,
            AuthenticationService authentication, AuditLogService audit, AuditLogRepository auditLogs) {
        this.google = google; this.apple = apple; this.challenges = challenges; this.appleOAuth = appleOAuth;
        this.identities = identities; this.users = users; this.companies = companies;
        this.passwords = passwords; this.jwt = jwt; this.authentication = authentication; this.audit = audit;
        this.auditLogs = auditLogs;
    }

    @Transactional
    public AuthResponse google(GoogleAuthRequest request) {
        return authenticate(google.verify(request.getIdToken()), request.getPreferredUsername(), null);
    }

    @Transactional
    public AuthResponse apple(AppleAuthRequest request) {
        appleOAuth.requireConfigured();
        String nonce = challenges.consume(request.challengeId());
        var identity = apple.verify(request.idToken(), nonce, request.fullName());
        var tokens = appleOAuth.exchange(request.authorizationCode());
        var exchanged = apple.verify(tokens.idToken(), nonce, request.fullName());
        if (!identity.subject().equals(exchanged.subject())) throw new BadCredentialsException("Apple giriş kimliği eşleşmedi.");
        return authenticate(identity, null, tokens.refreshTokenCiphertext());
    }

    private AuthResponse authenticate(VerifiedSocialIdentity verified, String preferredUsername, String refreshToken) {
        SocialAuthIdentity identity = identities.findByProviderAndSubject(verified.provider(), verified.subject()).orElse(null);
        User user;
        if (identity != null) {
            // A deleted account must never be silently resurrected.
            user = users.findById(identity.getUserId()).orElseThrow(() -> new BadCredentialsException("Bu hesap artık aktif değil."));
        } else {
            String email = verified.email();
            if (email == null || email.isBlank() || email.length() > 320) throw new BadCredentialsException("Doğrulanmış e-posta gerekli.");
            email = email.trim().toLowerCase(Locale.ROOT);
            var matches = new ArrayList<>(users.findAllByUsernameIgnoreCase(email));
            if ("GOOGLE".equals(verified.provider())) {
                for (var registration : auditLogs.findLegacyGoogleRegistrations("Google ile bireysel kayıt: " + email)) {
                    users.findByIdAndCompanyId(registration.getUserId(), registration.getCompanyId()).ifPresent(legacy -> {
                        if (matches.stream().noneMatch(candidate -> candidate.getId().equals(legacy.getId()))) matches.add(legacy);
                    });
                }
            }
            if (matches.size() > 1) throw new BadCredentialsException("Bu e-posta birden fazla kurumda kayıtlı. Kurum kodunuzla giriş yapın.");
            if (!matches.isEmpty()) {
                user = matches.get(0);
                if (!verified.emailAuthoritative() || "SUPER_ADMIN".equals(user.getRole()))
                    throw new BadCredentialsException("Bu hesap için kullanıcı adı ve şifrenizle giriş yapın.");
                if (identities.findByUserIdAndProvider(user.getId(), verified.provider()).isPresent())
                    throw new BadCredentialsException("Bu hesap farklı bir sağlayıcı kimliğine bağlı.");
            } else {
                String username = preferredUsername == null || preferredUsername.isBlank() ? email : preferredUsername.trim();
                if (username.length() > 255 || !users.findAllByUsernameIgnoreCase(username).isEmpty())
                    throw new BadCredentialsException("Bu kullanıcı adı zaten kullanılıyor.");
                String name = verified.fullName() == null || verified.fullName().isBlank() ? email : verified.fullName().trim();
                if (name.length() > 200) name = name.substring(0, 200);
                var company = new Company(); CompanyAccessPolicy.initializeFreePlan(company);
                company.setName(name + " Servisi"); company.setEmail(email); company.setBillingEmail(email);
                company.setOrgCode("PUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT));
                companies.save(company);
                user = User.builder().companyId(company.getId()).username(username).fullName(name)
                        .passwordHash(passwords.encode(UUID.randomUUID().toString())).role("COMPANY_ADMIN").build();
                user.setLocalPasswordEnabled(false);
                users.save(user);
                audit.logAuth(user.getCompanyId(), user.getId(), user.getFullName(), "USER_REGISTERED_" + verified.provider(),
                        "Sosyal giriş ile işletme hesabı oluşturuldu", null);
            }
            identity = new SocialAuthIdentity(); identity.setProvider(verified.provider());
            identity.setSubject(verified.subject()); identity.setUserId(user.getId()); identity.setVerifiedEmail(email);
        }
        if (refreshToken != null) identity.setRefreshTokenCiphertext(refreshToken);
        identities.saveAndFlush(identity);
        var response = authentication.getFeatureContextResponse(user);
        response.setToken(jwt.generateToken(user));
        response.setRequiresPasswordSetup(!user.isLocalPasswordEnabled());
        audit.logAuth(user.getCompanyId(), user.getId(), user.getFullName(), "LOGIN_SUCCESS_" + verified.provider(),
                "Sosyal giriş başarılı", null);
        return response;
    }
}
