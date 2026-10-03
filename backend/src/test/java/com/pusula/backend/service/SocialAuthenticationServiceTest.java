package com.pusula.backend.service;

import com.pusula.backend.dto.*;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocialAuthenticationServiceTest {
    private final GoogleIdentityTokenVerifier google = mock(GoogleIdentityTokenVerifier.class);
    private final AppleIdentityTokenVerifier apple = mock(AppleIdentityTokenVerifier.class);
    private final AppleAuthChallengeService challenges = mock(AppleAuthChallengeService.class);
    private final AppleSignInOAuthClient oauth = mock(AppleSignInOAuthClient.class);
    private final SocialAuthIdentityRepository identities = mock(SocialAuthIdentityRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final JwtService jwt = mock(JwtService.class);
    private final AuthenticationService auth = mock(AuthenticationService.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final AuditLogRepository auditLogs = mock(AuditLogRepository.class);
    private final SocialAuthenticationService service = new SocialAuthenticationService(google, apple, challenges, oauth,
            identities, users, companies, passwords, jwt, auth, audit, auditLogs);

    @Test void legacyGoogleCustomUsernameKeepsItsOriginalCompany() {
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@gmail.com", "User", true));
        var registration = new AuditLog(); registration.setUserId(7L); registration.setCompanyId(10L);
        when(auditLogs.findLegacyGoogleRegistrations("Google ile bireysel kayıt: user@gmail.com")).thenReturn(List.of(registration));
        var user = user(7L); user.setUsername("custom-name");
        when(users.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(user));
        when(auth.getFeatureContextResponse(user)).thenReturn(new AuthResponse());
        service.google(googleRequest());
        verify(companies, never()).save(any()); verify(users, never()).save(any());
        var identity = org.mockito.ArgumentCaptor.forClass(SocialAuthIdentity.class);
        verify(identities).saveAndFlush(identity.capture()); assertEquals(7L, identity.getValue().getUserId());
    }

    @Test void legacyMatchMustBeUniqueAndAuthoritativeAndInsideRecordedTenant() {
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@example.com", "User", false));
        var registration = new AuditLog(); registration.setUserId(7L); registration.setCompanyId(10L);
        when(auditLogs.findLegacyGoogleRegistrations("Google ile bireysel kayıt: user@example.com")).thenReturn(List.of(registration));
        when(users.findByIdAndCompanyId(7L, 10L)).thenReturn(Optional.of(user(7L)));
        assertThrows(BadCredentialsException.class, () -> service.google(googleRequest()));
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@example.com", "User", true));
        when(users.findAllByUsernameIgnoreCase("user@example.com")).thenReturn(List.of(user(8L)));
        assertThrows(BadCredentialsException.class, () -> service.google(googleRequest()));
        verify(companies, never()).save(any()); verify(identities, never()).saveAndFlush(any());
        verify(users, times(2)).findByIdAndCompanyId(7L, 10L);
    }

    @Test void createsPerpetualFreeCompanyAndStableIdentityIncludingPreferredUsername() {
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@gmail.com", "User", true));
        when(companies.save(any())).thenAnswer(i -> { Company c = i.getArgument(0); c.setId(10L); return c; });
        when(users.save(any())).thenAnswer(i -> { User u = i.getArgument(0); u.setId(7L); return u; });
        when(auth.getFeatureContextResponse(any())).thenReturn(new AuthResponse());
        when(jwt.generateToken(any())).thenReturn("jwt");
        var request = googleRequest(); request.setPreferredUsername("custom-name");
        assertEquals("jwt", service.google(request).getToken());
        var company = org.mockito.ArgumentCaptor.forClass(Company.class); verify(companies).save(company.capture());
        assertEquals("ACTIVE", company.getValue().getSubscriptionStatus()); assertNull(company.getValue().getTrialEndsAt());
        var identity = org.mockito.ArgumentCaptor.forClass(SocialAuthIdentity.class); verify(identities).saveAndFlush(identity.capture());
        assertEquals("sub", identity.getValue().getSubject()); assertEquals(7L, identity.getValue().getUserId());
        var user = org.mockito.ArgumentCaptor.forClass(User.class); verify(users).save(user.capture());
        assertEquals("custom-name", user.getValue().getUsername()); assertEquals("COMPANY_ADMIN", user.getValue().getRole());
    }
    @Test void providerSubjectWinsOverChangedEmailWithoutCreatingAnotherCompany() {
        var identity = new SocialAuthIdentity(); identity.setUserId(7L);
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "changed@gmail.com", null, true));
        when(identities.findByProviderAndSubject("GOOGLE", "sub")).thenReturn(Optional.of(identity));
        var user = user(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        when(auth.getFeatureContextResponse(user)).thenReturn(new AuthResponse());
        service.google(googleRequest());
        verify(users, never()).findAllByUsernameIgnoreCase(any()); verify(companies, never()).save(any());
    }
    @Test void safelyLinksSingleVerifiedEmailAndKeepsExistingRoleAndName() {
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@gmail.com", "Changed", true));
        var user = user(7L); user.setRole("TECHNICIAN"); user.setFullName("Existing");
        when(users.findAllByUsernameIgnoreCase("user@gmail.com")).thenReturn(List.of(user));
        when(auth.getFeatureContextResponse(user)).thenReturn(new AuthResponse());
        service.google(googleRequest());
        assertEquals("TECHNICIAN", user.getRole()); assertEquals("Existing", user.getFullName()); verify(companies, never()).save(any());
    }
    @Test void rejectsAmbiguousTenantAndNonAuthoritativeEmailLinking() {
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@example.com", "User", false));
        when(users.findAllByUsernameIgnoreCase("user@example.com")).thenReturn(List.of(user(1L), user(2L)));
        assertThrows(BadCredentialsException.class, () -> service.google(googleRequest()));
        when(users.findAllByUsernameIgnoreCase("user@example.com")).thenReturn(List.of(user(1L)));
        assertThrows(BadCredentialsException.class, () -> service.google(googleRequest()));
        verify(companies, never()).save(any()); verify(identities, never()).saveAndFlush(any());
    }
    @Test void cannotResurrectDeletedAccountOrLinkAnotherSubjectForSameProvider() {
        when(google.verify("token")).thenReturn(new VerifiedSocialIdentity("GOOGLE", "sub", "user@gmail.com", "User", true));
        var old = new SocialAuthIdentity(); old.setUserId(7L);
        when(identities.findByProviderAndSubject("GOOGLE", "sub")).thenReturn(Optional.of(old));
        assertThrows(BadCredentialsException.class, () -> service.google(googleRequest()));
        when(identities.findByProviderAndSubject("GOOGLE", "sub")).thenReturn(Optional.empty());
        when(users.findAllByUsernameIgnoreCase("user@gmail.com")).thenReturn(List.of(user(7L)));
        when(identities.findByUserIdAndProvider(7L, "GOOGLE")).thenReturn(Optional.of(old));
        assertThrows(BadCredentialsException.class, () -> service.google(googleRequest()));
    }
    @Test void appleRejectsSwappedCodeAndTokenBeforeAccountChanges() {
        when(challenges.consume("challenge")).thenReturn("nonce");
        when(apple.verify("native", "nonce", null)).thenReturn(new VerifiedSocialIdentity("APPLE", "sub1", null, null, false));
        when(oauth.exchange("code")).thenReturn(new AppleSignInOAuthClient.Tokens("exchanged", "encrypted"));
        when(apple.verify("exchanged", "nonce", null)).thenReturn(new VerifiedSocialIdentity("APPLE", "sub2", null, null, false));
        assertThrows(BadCredentialsException.class, () -> service.apple(new AppleAuthRequest("native", "code", "challenge", null)));
        verifyNoInteractions(users, companies, identities);
    }
    @Test void appleReturningIdentityNeedsNoEmailAndStoresOnlyEncryptedRefreshToken() {
        var identity = new SocialAuthIdentity(); identity.setUserId(7L);
        when(challenges.consume("challenge")).thenReturn("nonce");
        var verified = new VerifiedSocialIdentity("APPLE", "sub", null, null, false);
        when(apple.verify("native", "nonce", null)).thenReturn(verified);
        when(oauth.exchange("code")).thenReturn(new AppleSignInOAuthClient.Tokens("exchanged", "ciphertext"));
        when(apple.verify("exchanged", "nonce", null)).thenReturn(verified);
        when(identities.findByProviderAndSubject("APPLE", "sub")).thenReturn(Optional.of(identity));
        var user = user(7L); when(users.findById(7L)).thenReturn(Optional.of(user));
        when(auth.getFeatureContextResponse(user)).thenReturn(new AuthResponse());
        service.apple(new AppleAuthRequest("native", "code", "challenge", null));
        assertEquals("ciphertext", identity.getRefreshTokenCiphertext()); verify(companies, never()).save(any());
    }
    private GoogleAuthRequest googleRequest() { var r = new GoogleAuthRequest(); r.setIdToken("token"); return r; }
    private User user(long id) { var u = new User(); u.setId(id); u.setCompanyId(10L); u.setRole("COMPANY_ADMIN"); return u; }
}
