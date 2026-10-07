package com.pusula.backend.service;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.dto.AuthResponse;
import com.pusula.backend.dto.QuotaDTO;
import com.pusula.backend.dto.RegisterRequest;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthenticationService {

        private final UserRepository userRepository;
        private final CompanyRepository companyRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final AuditLogService auditLogService;
        private final FeatureService featureService;

        private final UserAccountDeletionService accountDeletion;

        public AuthenticationService(UserRepository userRepository,
                        CompanyRepository companyRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthenticationManager authenticationManager,
                        AuditLogService auditLogService,
                        FeatureService featureService,
                        UserAccountDeletionService accountDeletion) {
                this.userRepository = userRepository;
                this.companyRepository = companyRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.authenticationManager = authenticationManager;
                this.auditLogService = auditLogService;
                this.featureService = featureService;
                this.accountDeletion = accountDeletion;
        }

        /**
         * Individual registration — creates a new Company + Admin user.
         * Used by independent technicians who download from App Store.
         * Auto-assigns the perpetual free CIRAK plan.
         */
        @org.springframework.transaction.annotation.Transactional
        public AuthResponse registerIndividual(RegisterRequest request) {
                com.pusula.backend.util.PasswordPolicy.requireStrong(request.getPassword());
                String username = request.getUsername() != null && !request.getUsername().isBlank()
                                ? request.getUsername().trim() : request.getEmail();
                if (username == null || username.isBlank()) {
                        throw new BadCredentialsException("Kullanıcı adı veya e-posta gerekli");
                }
                if (!userRepository.findAllByUsernameIgnoreCase(username).isEmpty()) {
                        throw new BadCredentialsException("Bu kullanıcı adı zaten kullanılıyor");
                }
                // 1. Generate unique org code
                String orgCode = generateOrgCode();

                // 2. Create company with free plan
                Company company = new Company();
                company.setName(request.getFullName() != null
                                ? request.getFullName() + " Servisi"
                                : "Yeni Servis");
                CompanyAccessPolicy.initializeFreePlan(company);
                company.setOrgCode(orgCode);
                company.setEmail(request.getEmail());
                company.setBillingEmail(request.getEmail());
                companyRepository.save(company);

                // 3. Create admin user
                var user = User.builder()
                                .companyId(company.getId())
                                .username(username)
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .fullName(request.getFullName())
                                .role("COMPANY_ADMIN")
                                .build();
                userRepository.save(user);

                var jwtToken = jwtService.generateToken(user);

                // Log registration
                auditLogService.logAuth(
                                user.getCompanyId(),
                                user.getId(),
                                user.getFullName(),
                                "USER_REGISTERED_INDIVIDUAL",
                                "Bireysel kayıt: " + user.getUsername() + " (Org: " + orgCode + ")",
                                getClientIpAddress());

                return buildAuthResponse(jwtToken, user, company);
        }

        /**
         * Corporate registration — adds a user to an existing company.
         * Used by B2B enterprise clients org admin to add technicians.
         */
        public AuthResponse register(RegisterRequest request) {
                com.pusula.backend.util.PasswordPolicy.requireStrong(request.getPassword());
                String requestedRole = request.getRole() == null ? "" : request.getRole().trim().toUpperCase(Locale.ROOT);
                if (!"COMPANY_ADMIN".equals(requestedRole) && !"TECHNICIAN".equals(requestedRole)) {
                        throw new IllegalArgumentException("Geçersiz şirket kullanıcı rolü");
                }
                featureService.checkQuota(request.getCompanyId(),
                                "COMPANY_ADMIN".equals(requestedRole) ? "COMPANY_ADMINS" : "TECHNICIANS");
                var user = User.builder()
                                .companyId(request.getCompanyId())
                                .username(request.getUsername())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .fullName(request.getFullName())
                                .role(requestedRole)
                                .build();
                userRepository.save(user);
                var jwtToken = jwtService.generateToken(user);

                // Log user registration
                auditLogService.logAuth(
                                user.getCompanyId(),
                                user.getId(),
                                user.getFullName(),
                                "USER_REGISTERED",
                                "Yeni kullanıcı kaydı: " + user.getUsername(),
                                getClientIpAddress());

                Company company = companyRepository.findById(user.getCompanyId()).orElse(null);
                return buildAuthResponse(jwtToken, user, company);
        }

        /**
         * Authenticate — supports both individual (username/password) and
         * corporate (orgCode + username/password) login flows.
         */
        public AuthResponse authenticate(AuthRequest request) {
                String ipAddress = getClientIpAddress();

                try {
                        // Corporate flow: resolve company by org code first
                        Company company = null;
                        User user;

                        if (request.getOrgCode() != null && !request.getOrgCode().isEmpty()) {
                                // B2B Corporate login
                                String normalizedOrgCode = request.getOrgCode().trim().toUpperCase(Locale.ROOT);
                                company = companyRepository.findByOrgCodeIgnoreCase(normalizedOrgCode)
                                                .orElseThrow(() -> new BadCredentialsException(
                                                                "Kurum kodu bulunamadı: " + normalizedOrgCode));

                                user = userRepository.findByUsernameAndCompanyId(
                                                request.getUsername(), company.getId())
                                                .orElseThrow(() -> new BadCredentialsException(
                                                                "Kullanıcı bulunamadı"));

                                // Validate password manually for company-scoped auth
                                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                                        throw new BadCredentialsException("Hatalı şifre");
                                }
                        } else {
                                // Individual login — username is unique per company; resolve tenant safely.
                                List<User> matches = userRepository.findAllByUsername(request.getUsername());
                                if (matches.isEmpty()) {
                                        throw new BadCredentialsException("Kullanıcı bulunamadı");
                                }
                                if (matches.size() > 1) {
                                        throw new BadCredentialsException(
                                                        "Bu kullanıcı adı birden fazla kurumda kayıtlı. "
                                                                        + "Lütfen Kurumsal giriş sekmesinden kurum kodunuzla giriş yapın.");
                                }
                                user = matches.get(0);
                                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                                        throw new BadCredentialsException("Hatalı şifre");
                                }
                                company = companyRepository.findById(user.getCompanyId()).orElse(null);
                        }

                        var jwtToken = jwtService.generateToken(user);

                        // Log successful login
                        auditLogService.logAuth(
                                        user.getCompanyId(),
                                        user.getId(),
                                        user.getFullName(),
                                        "LOGIN_SUCCESS",
                                        "Giriş başarılı: " + user.getUsername(),
                                        ipAddress);

                        return buildAuthResponse(jwtToken, user, company);

                } catch (BadCredentialsException e) {
                        // Log failed login attempt
                        var userOpt = userRepository.findByUsername(request.getUsername());
                        Long companyId = userOpt.map(User::getCompanyId).orElse(0L);

                        auditLogService.logAuth(
                                        companyId,
                                        0L,
                                        request.getUsername(),
                                        "LOGIN_FAILED",
                                        "Başarısız giriş denemesi: " + request.getUsername(),
                                        ipAddress);

                        throw e;
                }
        }

        /**
         * Re-authenticates the already authenticated user for sensitive actions.
         * The identity comes exclusively from the JWT security context; a client
         * supplied username or organization code is intentionally ignored.
         */
        public boolean verifyCurrentUserPassword(String password) {
                if (password == null || password.isBlank()) {
                        return false;
                }
                var authentication = SecurityContextHolder.getContext().getAuthentication();
                if (authentication == null || !(authentication.getPrincipal() instanceof User principal)) {
                        return false;
                }
                User currentUser = userRepository.findByIdAndCompanyId(principal.getId(), principal.getCompanyId())
                                .orElse(null);
                return currentUser != null
                                && passwordEncoder.matches(password, currentUser.getPasswordHash());
        }


        /**
         * Deletes the currently authenticated user's account.
         * Used to comply with App Store account deletion guidelines.
         */
        @org.springframework.transaction.annotation.Transactional
        public void deleteAccount() {
                User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                accountDeletion.delete(currentUser);
                // Record only a non-personal deletion marker, not the former name/login/IP.
                auditLogService.logAuth(
                        currentUser.getCompanyId(),
                        currentUser.getId(),
                        "Silinen kullanıcı",
                        "ACCOUNT_DELETED",
                        "Kullanıcı hesabı ve kişisel giriş verileri silindi",
                        null);
        }

        // ── Helper Methods ──────────────────────────────────────────────

        private AuthResponse buildAuthResponse(String token, User user, Company company) {
                Map<String, Boolean> features = company != null
                                ? featureService.getFeatureFlags(company.getPlanType())
                                : Collections.emptyMap();

                Integer trialDays = CompanyAccessPolicy.trialDaysRemaining(company);
                boolean isReadOnly = CompanyAccessPolicy.isReadOnly(company);

                AuthResponse response = AuthResponse.builder()
                                .token(token)
                                .role(user.getRole())
                                .fullName(user.getFullName())
                                .companyId(user.getCompanyId())
                                .companyName(company != null ? company.getName() : null)
                                .planType(company != null ? company.getPlanType().name() : "CIRAK")
                                .features(features)
                                .quota(company != null ? featureService.getQuota(company.getId()) : QuotaDTO.unlimited())
                                .readOnly(isReadOnly)
                                .trialDaysRemaining(trialDays)
                                .onboardingVersion(user.getMobileOnboardingVersion())
                                .build();
                response.setRequiresPasswordSetup(!user.isLocalPasswordEnabled());
                response.setLoginUsername(user.getUsername());
                return response;
        }

        @org.springframework.transaction.annotation.Transactional
        public void setInitialSocialPassword(String password) {
                com.pusula.backend.util.PasswordPolicy.requireStrong(password);
                if (password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                        throw new IllegalArgumentException("Şifre UTF-8 olarak en fazla 72 bayt olabilir.");
                }
                var principal = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                var user = userRepository.findByIdAndCompanyId(principal.getId(), principal.getCompanyId())
                                .orElseThrow(() -> new BadCredentialsException("Kullanıcı bulunamadı."));
                if (user.isLocalPasswordEnabled()) {
                        throw new IllegalStateException("Şifreniz zaten tanımlı. Mevcut şifre sıfırlama akışını kullanın.");
                }
                user.setPasswordHash(passwordEncoder.encode(password));
                user.setLocalPasswordEnabled(true);
                userRepository.save(user);
                auditLogService.logAuth(user.getCompanyId(), user.getId(), user.getFullName(),
                                "SOCIAL_PASSWORD_INITIALIZED", "Yönetici işlem şifresi tanımlandı", getClientIpAddress());
        }

        public int updateMobileOnboardingVersion(User user, Integer requestedVersion) {
                if (requestedVersion == null || requestedVersion < 0 || requestedVersion > 1000) {
                        throw new IllegalArgumentException("Geçersiz onboarding sürümü.");
                }
                int effectiveVersion = Math.max(user.getMobileOnboardingVersion(), requestedVersion);
                if (effectiveVersion != user.getMobileOnboardingVersion()) {
                        user.setMobileOnboardingVersion(effectiveVersion);
                        userRepository.save(user);
                }
                return effectiveVersion;
        }

        public AuthResponse getFeatureContextResponse(User user) {
                Company company = companyRepository.findById(user.getCompanyId())
                                .orElseThrow(() -> new RuntimeException("Şirket bulunamadı"));
                return buildAuthResponse(null, user, company);
        }

        private String generateOrgCode() {
                return "PUS-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        }

        private String getClientIpAddress() {
                try {
                        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder
                                        .getRequestAttributes();
                        if (attrs != null) {
                                HttpServletRequest request = attrs.getRequest();
                                String xForwardedFor = request.getHeader("X-Forwarded-For");
                                if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
                                        return xForwardedFor.split(",")[0].trim();
                                }
                                return request.getRemoteAddr();
                        }
                } catch (Exception e) {
                        // Ignore
                }
                return null;
        }
}
