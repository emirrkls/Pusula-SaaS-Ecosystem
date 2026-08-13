package com.pusula.backend.service;

import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;
import com.pusula.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthenticationServicePasswordVerificationTest {
    @Mock UserRepository userRepository;
    @Mock CompanyRepository companyRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock AuthenticationManager authenticationManager;
    @Mock AuditLogService auditLogService;
    private AuthenticationService service;

    @BeforeEach
    void setUp() {
        service = new AuthenticationService(userRepository, companyRepository, passwordEncoder,
                jwtService, authenticationManager, auditLogService);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void verifiesAuthenticatedUserWithinTheirCompanyWithoutUsingUsername() {
        User principal = user(44L, 9L, "firma-yöneticisi", "jwt-copy");
        User persisted = user(44L, 9L, "firma-yöneticisi", "bcrypt-hash");
        authenticate(principal);
        when(userRepository.findByIdAndCompanyId(44L, 9L)).thenReturn(Optional.of(persisted));
        when(passwordEncoder.matches("secret", "bcrypt-hash")).thenReturn(true);

        assertTrue(service.verifyCurrentUserPassword("secret"));

        verify(userRepository).findByIdAndCompanyId(44L, 9L);
        verify(userRepository, never()).findByUsername("admin");
        verify(userRepository, never()).findAllByUsername("firma-yöneticisi");
    }

    @Test
    void rejectsWrongPasswordMissingUserAndUnauthenticatedRequest() {
        User principal = user(44L, 9L, "onur", "jwt-copy");
        User persisted = user(44L, 9L, "onur", "bcrypt-hash");
        authenticate(principal);
        when(userRepository.findByIdAndCompanyId(44L, 9L)).thenReturn(Optional.of(persisted));
        when(passwordEncoder.matches("wrong", "bcrypt-hash")).thenReturn(false);
        assertFalse(service.verifyCurrentUserPassword("wrong"));

        when(userRepository.findByIdAndCompanyId(44L, 9L)).thenReturn(Optional.empty());
        assertFalse(service.verifyCurrentUserPassword("secret"));

        SecurityContextHolder.clearContext();
        assertFalse(service.verifyCurrentUserPassword("secret"));
        assertFalse(service.verifyCurrentUserPassword(" "));
    }

    private void authenticate(User principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private User user(Long id, Long companyId, String username, String hash) {
        User user = new User();
        user.setId(id);
        user.setCompanyId(companyId);
        user.setUsername(username);
        user.setPasswordHash(hash);
        user.setRole("COMPANY_ADMIN");
        return user;
    }
}
