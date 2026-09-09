package com.pusula.backend.service;

import com.pusula.backend.dto.AccountPartyDTO;
import com.pusula.backend.entity.AccountParty;
import com.pusula.backend.repository.AccountPartyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountPartyServiceTest {
    @Mock AccountPartyRepository repository;
    @Mock AuditLogService auditLogService;
    private AccountPartyService service;

    @BeforeEach void setUp() { service = new AccountPartyService(repository, auditLogService); }

    @Test void createsTenantScopedOrganizationWithNormalizedIdentity() {
        when(repository.findByCompanyIdAndPartyTypeAndNormalizedName(7L,
                AccountParty.PartyType.ORGANIZATION, "termodinamik a.ş.")).thenReturn(Optional.empty());
        when(repository.save(any(AccountParty.class))).thenAnswer(invocation -> {
            AccountParty party = invocation.getArgument(0); party.setId(44L); return party;
        });

        AccountPartyDTO result = service.create(7L, AccountPartyDTO.builder()
                .partyType(AccountParty.PartyType.ORGANIZATION)
                .displayName("  Termodinamik   A.Ş. ").paymentTermDays(30).build());

        assertEquals(44L, result.getId());
        assertEquals("Termodinamik A.Ş.", result.getDisplayName());
        verify(repository).save(argThat(party -> party.getCompanyId().equals(7L)
                && party.getNormalizedName().equals("termodinamik a.ş.")
                && party.getPartyType() == AccountParty.PartyType.ORGANIZATION));
    }

    @Test void rejectsManualCustomerCards() {
        assertThrows(IllegalArgumentException.class, () -> service.create(7L, AccountPartyDTO.builder()
                .partyType(AccountParty.PartyType.CUSTOMER).displayName("Manuel müşteri").build()));
        verify(repository, never()).save(any());
    }

    @Test void neverReturnsPartyFromAnotherTenant() {
        when(repository.findByIdAndCompanyId(44L, 7L)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getEntity(7L, 44L));
    }
}
