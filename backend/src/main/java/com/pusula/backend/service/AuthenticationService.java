package com.pusula.backend.service;

import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.dto.AuthResponse;
import com.pusula.backend.dto.RegisterRequest;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthenticationService {

        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final JwtService jwtService;
        private final AuthenticationManager authenticationManager;
        private final AuditLogService auditLogService;

        public AuthenticationService(UserRepository userRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService,
                        AuthenticationManager authenticationManager,
                        AuditLogService auditLogService) {
                this.userRepository = userRepository;
                this.passwordEncoder = passwordEncoder;
                this.jwtService = jwtService;
                this.authenticationManager = authenticationManager;
                this.auditLogService = auditLogService;
        }

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

                return AuthResponse.builder()
                                .token(jwtToken)
                                .role(user.getRole())
                                .build();
        }

        public AuthResponse authenticate(AuthRequest request) {
                String ipAddress = getClientIpAddress();

                try {
                        authenticationManager.authenticate(
                                        new UsernamePasswordAuthenticationToken(
                                                        request.getUsername(),
                                                        request.getPassword()));

                        var user = userRepository.findByUsername(request.getUsername())
                                        .orElseThrow();
                        var jwtToken = jwtService.generateToken(user);

                        // Log successful login
                        auditLogService.logAuth(
                                        user.getCompanyId(),
                                        user.getId(),
                                        user.getFullName(),
                                        "LOGIN_SUCCESS",
                                        "Giriş başarılı: " + user.getUsername(),
                                        ipAddress);

                        return AuthResponse.builder()
                                        .token(jwtToken)
                                        .role(user.getRole())
                                        .build();
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
