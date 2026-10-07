package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UserAccountDeletionServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final SocialAccountDeletionService social = mock(SocialAccountDeletionService.class);
    private final PushDeviceRepository devices = mock(PushDeviceRepository.class);
    private final AuditLogRepository audits = mock(AuditLogRepository.class);
    private final SignatureErasureTaskRepository signatures = mock(SignatureErasureTaskRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final CompanyRepository companies = mock(CompanyRepository.class);
    private final UserAccountDeletionService service = new UserAccountDeletionService(
            users, social, devices, audits, signatures, passwords, companies);

    @Test void clearsPersonalFieldsAndDevicesButPreservesBusinessForeignKey() {
        var user = user(); when(users.lockByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(user));
        when(passwords.encode(anyString())).thenReturn("irreversible-random-hash");
        when(users.findByCompanyId(7L)).thenReturn(List.of(new User()));
        service.delete(user);
        assertTrue(user.isDeleted()); assertFalse(user.isEnabled()); assertFalse(user.isLocalPasswordEnabled());
        assertEquals(9L, user.getId()); assertEquals(7L, user.getCompanyId());
        assertNotEquals("personal@example.com", user.getUsername()); assertEquals("Silinen kullanıcı", user.getFullName());
        assertNull(user.getSignaturePath()); assertEquals("irreversible-random-hash", user.getPasswordHash());
        verify(social).revokeAndRemove(9L); verify(devices).deleteByCompanyIdAndUserId(7L, 9L);
        verify(audits).deletePersonalAuthenticationHistory(9L); verify(audits).anonymizeActor(9L);
        verify(audits).eraseUserProfileSnapshots(9L); verify(signatures).save(any(SignatureErasureTask.class));
        verify(companies, never()).delete(any()); verify(users, never()).delete(any());
    }

    @Test void appleRevocationFailureDoesNotEraseAnythingOrReportSuccess() {
        var user = user(); when(users.lockByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(user));
        doThrow(new IllegalStateException()).when(social).revokeAndRemove(9L);
        assertThrows(IllegalStateException.class, () -> service.delete(user));
        assertFalse(user.isDeleted()); assertEquals("personal@example.com", user.getUsername());
        verifyNoInteractions(devices, audits, signatures, passwords, companies);
        verify(users, never()).saveAndFlush(any());
    }

    @Test void lastIndividualUserAlsoRemovesPersonalCompanyRegistrationDefaults() {
        var user = user(); var company = new Company(); company.setId(7L);
        company.setName("Personal Name Servisi"); company.setEmail(user.getUsername());
        company.setBillingEmail(user.getUsername());
        when(users.lockByIdAndCompanyId(9L, 7L)).thenReturn(Optional.of(user));
        when(users.findByCompanyId(7L)).thenReturn(List.of());
        when(companies.lockById(7L)).thenReturn(Optional.of(company));
        service.delete(user);
        assertNull(company.getEmail()); assertNull(company.getBillingEmail());
        assertEquals("Hesabı silinen işletme", company.getName());
        verify(companies).save(company);
    }

    @Test void tenantMismatchIsRejectedBeforeAnyMutation() {
        assertThrows(NoSuchElementException.class, () -> service.delete(user()));
        verifyNoInteractions(social, devices, audits, signatures, passwords, companies);
    }

    private User user() {
        var user = new User(); user.setId(9L); user.setCompanyId(7L); user.setUsername("personal@example.com");
        user.setFullName("Personal Name"); user.setPasswordHash("old-hash");
        user.setSignaturePath("signatures/9/signature.png"); return user;
    }
}
