package com.pusula.backend.service;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.dto.AuthResponse;
import com.pusula.backend.dto.GoogleAuthRequest;
import com.pusula.backend.dto.QuotaDTO;
import com.pusula.backend.dto.RegisterRequest;
import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.PlanType;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.UUID;

@Service
public class AuthenticationService {

        private final UserRepository userRepository;
        private final CompanyRepository companyRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final AuditLogService auditLogService;

        @Value("${google.oauth.web-client-id:}")
        private String googleWebClientId;

        public AuthenticationService(UserRepository userRepository,
                        CompanyRepository companyRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthenticationManager authenticationManager,
                        AuditLogService auditLogService) {
                this.userRepository = userRepository;
                this.companyRepository = companyRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.authenticationManager = authenticationManager;
                this.auditLogService = auditLogService;
        }

        /**
         * Individual registration — creates a new Company + Admin user.
         * Used by independent technicians who download from App Store.
         * Auto-assigns CIRAK plan with 14-day trial.
         */
        public AuthResponse registerIndividual(RegisterRequest request) {
                // 1. Generate unique org code
                String orgCode = generateOrgCode();

                // 2. Create company with free plan
                Company company = new Company();
                company.setName(request.getFullName() != null
                                ? request.getFullName() + " Servisi"
                                : "Yeni Servis");
                company.setSubscriptionStatus("TRIAL");
                company.setPlanType(PlanType.CIRAK);
                company.setTrialEndsAt(LocalDateTime.now().plusDays(14));
                company.setOrgCode(orgCode);
                company.setEmail(request.getEmail());
                company.setBillingEmail(request.getEmail());
                companyRepository.save(company);

                // 3. Create admin user
                String username = request.getUsername() != null && !request.getUsername().isBlank()
                                ? request.getUsername().trim()
                                : request.getEmail();
                if (username == null || username.isBlank()) {
                        throw new BadCredentialsException("Kullanıcı adı veya e-posta gerekli");
                }
                if (userRepository.findByUsername(username).isPresent()) {
                        throw new BadCredentialsException("Bu kullanıcı adı zaten kullanılıyor");
                }
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
                var user = User.builder()
                                .companyId(request.getCompanyId())
                                .username(request.getUsername())
                                .passwordHash(passwordEncoder.encode(request.getPassword()))
                                .fullName(request.getFullName())
                                .role(request.getRole())
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
                                company = companyRepository.findByOrgCode(request.getOrgCode())
                                                .orElseThrow(() -> new BadCredentialsException(
                                                                "Kurum kodu bulunamadı: " + request.getOrgCode()));

                                user = userRepository.findByUsernameAndCompanyId(
                                                request.getUsername(), company.getId())
                                                .orElseThrow(() -> new BadCredentialsException(
                                                                "Kullanıcı bulunamadı"));

                                // Validate password manually for company-scoped auth
                                if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
                                        throw new BadCredentialsException("Hatalı şifre");
                                }
                        } else {
                                // Individual login — use Spring Security authentication manager
                                authenticationManager.authenticate(
                                                new UsernamePasswordAuthenticationToken(
                                                                request.getUsername(),
                                                                request.getPassword()));

                                user = userRepository.findByUsername(request.getUsername())
                                                .orElseThrow();
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

        public AuthResponse authenticateWithGoogle(GoogleAuthRequest request) {
                String ipAddress = getClientIpAddress();
                try {
                        if (request == null || request.getIdToken() == null || request.getIdToken().isBlank()) {
                                throw new BadCredentialsException("Google token gerekli");
                        }
                        if (googleWebClientId == null || googleWebClientId.isBlank()) {
                                throw new BadCredentialsException("Google OAuth yapılandırması eksik");
                        }

                        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                                        new NetHttpTransport(),
                                        JacksonFactory.getDefaultInstance())
                                        .setAudience(Collections.singletonList(googleWebClientId))
                                        .build();

                        GoogleIdToken googleIdToken = verifier.verify(request.getIdToken());
                        if (googleIdToken == null) {
                                throw new BadCredentialsException("Google token doğrulanamadı");
                        }

                        GoogleIdToken.Payload payload = googleIdToken.getPayload();
                        String email = payload.getEmail();
                        String fullName = (String) payload.get("name");

                        if (email == null || email.isBlank()) {
                                throw new BadCredentialsException("Google hesabından e-posta alınamadı");
                        }

                        User user = userRepository.findByUsername(email).orElse(null);
                        Company company;

                        if (user == null) {
                                String orgCode = generateOrgCode();
                                company = new Company();
                                company.setName((fullName != null && !fullName.isBlank() ? fullName : email) + " Servisi");
                                company.setSubscriptionStatus("TRIAL");
                                company.setPlanType(PlanType.CIRAK);
                                company.setTrialEndsAt(LocalDateTime.now().plusDays(14));
                                company.setOrgCode(orgCode);
                                company.setEmail(email);
                                company.setBillingEmail(email);
                                companyRepository.save(company);

                                String preferredUsername = request.getPreferredUsername();
                                String username = preferredUsername != null && !preferredUsername.isBlank()
                                                ? preferredUsername.trim()
                                                : email;
                                if (userRepository.findByUsername(username).isPresent()) {
                                        throw new BadCredentialsException("Bu kullanıcı adı zaten kullanılıyor");
                                }

                                user = User.builder()
                                                .companyId(company.getId())
                                                .username(username)
                                                .passwordHash(passwordEncoder.encode(UUID.randomUUID().toString()))
                                                .fullName(fullName != null && !fullName.isBlank() ? fullName : email)
                                                .role("COMPANY_ADMIN")
                                                .build();
                                userRepository.save(user);

                                auditLogService.logAuth(
                                                user.getCompanyId(),
                                                user.getId(),
                                                user.getFullName(),
                                                "USER_REGISTERED_GOOGLE",
                                                "Google ile bireysel kayıt: " + email,
                                                ipAddress);
                        } else {
                                company = companyRepository.findById(user.getCompanyId()).orElse(null);
                        }

                        String jwtToken = jwtService.generateToken(user);
                        auditLogService.logAuth(
                                        user.getCompanyId(),
                                        user.getId(),
                                        user.getFullName(),
                                        "LOGIN_SUCCESS_GOOGLE",
                                        "Google ile giriş başarılı: " + email,
                                        ipAddress);

                        return buildAuthResponse(jwtToken, user, company);
                } catch (BadCredentialsException e) {
                        throw e;
                } catch (Exception e) {
                        throw new BadCredentialsException("Google ile giriş başarısız");
                }
        }

        /**
         * Deletes the currently authenticated user's account.
         * Used to comply with App Store account deletion guidelines.
         */
        public void deleteAccount() {
                User currentUser = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
                
                // Log deletion
                auditLogService.logAuth(
                        currentUser.getCompanyId(),
                        currentUser.getId(),
                        currentUser.getFullName(),
                        "ACCOUNT_DELETED",
                        "Kullanıcı hesabını sildi: " + currentUser.getUsername(),
                        getClientIpAddress());
                        
                // Delete user (soft delete handled by @SQLDelete in User entity)
                userRepository.delete(currentUser);
        }

        // ── Helper Methods ──────────────────────────────────────────────

        private AuthResponse buildAuthResponse(String token, User user, Company company) {
                Map<String, Boolean> features = getDefaultFeatures(
                                company != null ? company.getPlanType() : PlanType.CIRAK);

                Integer trialDays = null;
                boolean isReadOnly = false;

                if (company != null) {
                        // Calculate trial days remaining
                        if (company.getTrialEndsAt() != null) {
                                long days = ChronoUnit.DAYS.between(LocalDateTime.now(), company.getTrialEndsAt());
                                trialDays = days > 0 ? (int) days : 0;
                                if (days <= 0 && "TRIAL".equals(company.getSubscriptionStatus())) {
                                        isReadOnly = true;
                                }
                        }
                        if ("SUSPENDED".equals(company.getSubscriptionStatus())) {
                                isReadOnly = true;
                        }
                }

                return AuthResponse.builder()
                                .token(token)
                                .role(user.getRole())
                                .fullName(user.getFullName())
                                .companyId(user.getCompanyId())
                                .companyName(company != null ? company.getName() : null)
                                .planType(company != null ? company.getPlanType().name() : "CIRAK")
                                .features(features)
                                .quota(QuotaDTO.unlimited()) // Will be populated by FeatureService in Sprint 2
                                .readOnly(isReadOnly)
                                .trialDaysRemaining(trialDays)
                                .build();
        }

        /**
         * Returns default feature flags based on plan tier.
         * In Sprint 2, this will be replaced by FeatureService with DB lookup.
         */
        private Map<String, Boolean> getDefaultFeatures(PlanType planType) {
                Map<String, Boolean> features = new HashMap<>();

                // Common features (all plans)
                features.put("SERVICE_TICKETS", true);
                features.put("CUSTOMER_MANAGEMENT", true);
                features.put("BASIC_INVENTORY", true);

                switch (planType) {
                        case PATRON:
                                features.put("COMMERCIAL_DEVICES", true);
                                features.put("COMPANY_DEBT_TRACKING", true);
                                features.put("CUSTOM_BRANDING", true);
                                // fall through to USTA
                        case USTA:
                                features.put("FINANCE_MODULE", true);
                                features.put("PDF_EXPORT", true);
                                features.put("PROPOSAL_MODULE", true);
                                features.put("VEHICLE_TRACKING", true);
                                features.put("AUDIT_LOGS", true);
                                features.put("WHATSAPP_INTEGRATION", true);
                                features.put("MULTI_TECHNICIAN", true);
                                features.put("DAILY_CLOSING", true);
                                break;
                        case CIRAK:
                        default:
                                features.put("FINANCE_MODULE", false);
                                features.put("PDF_EXPORT", false);
                                features.put("PROPOSAL_MODULE", false);
                                features.put("VEHICLE_TRACKING", false);
                                features.put("AUDIT_LOGS", false);
                                features.put("WHATSAPP_INTEGRATION", false);
                                features.put("MULTI_TECHNICIAN", false);
                                features.put("DAILY_CLOSING", false);
                                features.put("COMMERCIAL_DEVICES", false);
                                features.put("COMPANY_DEBT_TRACKING", false);
                                features.put("CUSTOM_BRANDING", false);
                                break;
                }

                return features;
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
