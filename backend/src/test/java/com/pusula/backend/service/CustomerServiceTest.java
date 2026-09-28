package com.pusula.backend.service;

import com.pusula.backend.entity.AccountParty;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.WhatsAppConsentSource;
import com.pusula.backend.dto.CustomerWhatsAppConsentDTO;
import com.pusula.backend.dto.UpdateCustomerWhatsAppConsentRequest;
import com.pusula.backend.repository.AccountPartyRepository;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ProposalRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {
    @Mock CustomerRepository customerRepository;
    @Mock UserRepository userRepository;
    @Mock ServiceTicketRepository serviceTicketRepository;
    @Mock ProposalRepository proposalRepository;
    @Mock CurrentAccountRepository currentAccountRepository;
    @Mock AccountPartyRepository accountPartyRepository;
    @Mock AuditLogService auditLogService;

    private CustomerService service;
    private User currentUser;

    @BeforeEach
    void setUp() {
        service = new CustomerService(customerRepository, userRepository, serviceTicketRepository,
                proposalRepository, currentAccountRepository, accountPartyRepository, auditLogService);
        currentUser = User.builder().id(7L).companyId(10L).username("admin").passwordHash("hash")
                .role("COMPANY_ADMIN").fullName("Admin").build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void unusedCustomerIsSoftDeletedWithinAuthenticatedCompany() {
        Customer customer = customer(25L, 10L, "Test Müşteri");
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(customer));
        when(currentAccountRepository.findByCustomerIdAndCompanyId(25L, 10L)).thenReturn(Optional.empty());

        service.deleteCustomer(25L);

        verify(customerRepository).delete(customer);
        verify(auditLogService).log("DELETE", "CUSTOMER", 25L, "Müşteri silindi: Test Müşteri");
    }

    @Test
    void customerWithBusinessHistoryCannotBeDeleted() {
        Customer customer = customer(25L, 10L, "Geçmişi Olan");
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(customer));
        when(serviceTicketRepository.existsByCompanyIdAndCustomerId(10L, 25L)).thenReturn(true);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.deleteCustomer(25L));

        assertEquals("Geçmiş iş emri, teklif veya cari hareketi bulunan müşteri silinemez. Müşteri bilgilerini düzenleyebilirsiniz.",
                error.getMessage());
        verify(customerRepository, never()).delete(any(Customer.class));
    }

    @Test
    void technicianCannotDeleteCustomer() {
        currentUser.setRole("TECHNICIAN");

        assertThrows(AccessDeniedException.class, () -> service.deleteCustomer(25L));

        verify(customerRepository, never()).findByIdAndCompanyId(any(), any());
        verify(customerRepository, never()).delete(any(Customer.class));
    }

    @Test
    void updateSynchronizesExistingCustomerCariCard() {
        Customer existing = customer(25L, 10L, "Eski Ad");
        Customer request = customer(null, null, "  Yeni Ad  ");
        request.setPhone(" 0555 000 00 00 ");
        request.setAddress(" Yeni adres ");
        AccountParty party = AccountParty.builder().id(30L).companyId(10L)
                .partyType(AccountParty.PartyType.CUSTOMER).displayName("Eski Ad")
                .normalizedName("customer-25").customerId(25L).active(true).build();
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(existing)).thenReturn(existing);
        when(accountPartyRepository.findByCompanyIdAndCustomerId(10L, 25L)).thenReturn(Optional.of(party));

        Customer result = service.updateCustomer(25L, request);

        assertEquals("Yeni Ad", result.getName());
        assertEquals("0555 000 00 00", result.getPhone());
        assertEquals("Yeni adres", result.getAddress());
        assertEquals("Yeni Ad", party.getDisplayName());
        verify(accountPartyRepository).save(party);
    }

    @Test
    void createCustomerDoesNotAcceptImplicitWhatsAppConsent() {
        Customer request = customer(null, null, "Yeni Müşteri");
        request.setWhatsappOptIn(true);
        request.setWhatsappOptInAt(LocalDateTime.now().minusDays(1));
        request.setWhatsappOptInSource(WhatsAppConsentSource.DIGITAL_FORM);
        when(customerRepository.save(request)).thenReturn(request);

        Customer saved = service.createCustomer(request);

        assertEquals(false, saved.isWhatsappOptIn());
        assertEquals(null, saved.getWhatsappOptInAt());
        assertEquals(null, saved.getWhatsappOptInSource());
        assertEquals(null, saved.getWhatsappOptOutAt());
        assertEquals(10L, saved.getCompanyId());
    }

    @Test
    void explicitConsentGrantIsTenantScopedAndAudited() {
        Customer customer = customer(25L, 10L, "İzinli Müşteri");
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);
        LocalDateTime before = LocalDateTime.now();

        CustomerWhatsAppConsentDTO result = service.updateWhatsAppConsent(25L,
                new UpdateCustomerWhatsAppConsentRequest(true, WhatsAppConsentSource.WRITTEN_FORM));

        assertEquals(true, result.optedIn());
        assertEquals(WhatsAppConsentSource.WRITTEN_FORM, result.source());
        assertEquals(null, result.optedOutAt());
        org.junit.jupiter.api.Assertions.assertNotNull(result.optedInAt());
        org.junit.jupiter.api.Assertions.assertFalse(result.optedInAt().isBefore(before));
        verify(customerRepository).findByIdAndCompanyId(25L, 10L);
        verify(auditLogService).log("UPDATE", "CUSTOMER_WHATSAPP_CONSENT", 25L,
                "Müşteri WhatsApp izni verildi. Kaynak: WRITTEN_FORM");
    }

    @Test
    void consentRevocationPreservesGrantEvidenceAndRecordsFirstOptOutTime() {
        Customer customer = customer(25L, 10L, "İzin İptali");
        LocalDateTime grantedAt = LocalDateTime.of(2026, 9, 1, 10, 30);
        customer.setWhatsappOptIn(true);
        customer.setWhatsappOptInAt(grantedAt);
        customer.setWhatsappOptInSource(WhatsAppConsentSource.VERBAL_CONFIRMATION);
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(customer)).thenReturn(customer);

        CustomerWhatsAppConsentDTO result = service.updateWhatsAppConsent(25L,
                new UpdateCustomerWhatsAppConsentRequest(false, null));

        assertEquals(false, result.optedIn());
        assertEquals(grantedAt, result.optedInAt());
        assertEquals(WhatsAppConsentSource.VERBAL_CONFIRMATION, result.source());
        org.junit.jupiter.api.Assertions.assertNotNull(result.optedOutAt());
    }

    @Test
    void consentGrantRequiresEvidenceSource() {
        Customer customer = customer(25L, 10L, "Kaynak Eksik");
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(customer));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.updateWhatsAppConsent(25L,
                        new UpdateCustomerWhatsAppConsentRequest(true, null)));

        assertEquals("WhatsApp izni verilirken izin kaynağı zorunludur.", error.getMessage());
        verify(customerRepository, never()).save(any(Customer.class));
    }

    @Test
    void adminReadsConsentFromAuthenticatedCompanyOnly() {
        Customer customer = customer(25L, 10L, "İzin Durumu");
        customer.setWhatsappOptIn(true);
        customer.setWhatsappOptInAt(LocalDateTime.of(2026, 9, 2, 9, 15));
        customer.setWhatsappOptInSource(WhatsAppConsentSource.WHATSAPP_CONVERSATION);
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(customer));

        CustomerWhatsAppConsentDTO result = service.getWhatsAppConsent(25L);

        assertEquals(25L, result.customerId());
        assertEquals(true, result.optedIn());
        assertEquals(WhatsAppConsentSource.WHATSAPP_CONVERSATION, result.source());
        verify(customerRepository).findByIdAndCompanyId(25L, 10L);
        verify(customerRepository, never()).findById(25L);
    }

    @Test
    void technicianCannotReadOrChangeWhatsAppConsent() {
        currentUser.setRole("TECHNICIAN");

        assertThrows(AccessDeniedException.class, () -> service.getWhatsAppConsent(25L));
        assertThrows(AccessDeniedException.class, () -> service.updateWhatsAppConsent(25L,
                new UpdateCustomerWhatsAppConsentRequest(true, WhatsAppConsentSource.OTHER)));

        verify(customerRepository, never()).findByIdAndCompanyId(any(), any());
    }

    @Test
    void regularCustomerUpdateCannotOverwriteConsentState() {
        Customer existing = customer(25L, 10L, "Eski Ad");
        LocalDateTime grantedAt = LocalDateTime.of(2026, 9, 1, 10, 30);
        existing.setWhatsappOptIn(true);
        existing.setWhatsappOptInAt(grantedAt);
        existing.setWhatsappOptInSource(WhatsAppConsentSource.DIGITAL_FORM);
        Customer request = customer(null, null, "Yeni Ad");
        request.setWhatsappOptIn(false);
        when(customerRepository.findByIdAndCompanyId(25L, 10L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(existing)).thenReturn(existing);

        Customer result = service.updateCustomer(25L, request);

        assertEquals(true, result.isWhatsappOptIn());
        assertEquals(grantedAt, result.getWhatsappOptInAt());
        assertEquals(WhatsAppConsentSource.DIGITAL_FORM, result.getWhatsappOptInSource());
    }

    private Customer customer(Long id, Long companyId, String name) {
        return Customer.builder().id(id).companyId(companyId).name(name).build();
    }
}
