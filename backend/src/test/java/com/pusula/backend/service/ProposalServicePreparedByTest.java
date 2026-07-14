package com.pusula.backend.service;

import com.pusula.backend.dto.ProposalDTO;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.Proposal;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ProposalItemRepository;
import com.pusula.backend.repository.ProposalRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalServicePreparedByTest {

    @Mock
    private ProposalRepository proposalRepository;
    @Mock
    private ProposalItemRepository proposalItemRepository;
    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceTicketRepository serviceTicketRepository;
    @Mock
    private FeatureService featureService;

    private ProposalService service;

    @BeforeEach
    void setUp() {
        service = new ProposalService(
                proposalRepository,
                proposalItemRepository,
                customerRepository,
                userRepository,
                serviceTicketRepository,
                featureService);
        when(proposalRepository.save(any(Proposal.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(customerRepository.findById(20L))
                .thenReturn(Optional.of(Customer.builder().id(20L).companyId(10L).name("Müşteri").build()));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void create_usesAuthenticatedUserInsteadOfClientPreparedById() {
        User currentUser = user(9L, "admin", "Gerçek Hazırlayan");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(9L)).thenReturn(Optional.of(currentUser));

        ProposalDTO result = service.create(updateRequest(999L));

        assertEquals(9L, result.getPreparedById());
        assertEquals("Gerçek Hazırlayan", result.getPreparedByName());
    }

    @Test
    void update_keepsOriginalPreparerWhenClientOmitsPreparedById() {
        Proposal proposal = proposal(7L);
        User originalPreparer = user(7L, "original", "İlk Hazırlayan");
        when(proposalRepository.findById(30L)).thenReturn(Optional.of(proposal));
        when(userRepository.findById(7L)).thenReturn(Optional.of(originalPreparer));

        ProposalDTO result = service.update(30L, updateRequest(null));

        assertEquals(7L, proposal.getPreparedById());
        assertEquals("İlk Hazırlayan", result.getPreparedByName());
    }

    @Test
    void update_assignsAuthenticatedUserToLegacyProposalWithoutPreparer() {
        Proposal proposal = proposal(null);
        User currentUser = user(9L, "admin", "Güncel Hazırlayan");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password"));
        when(proposalRepository.findById(30L)).thenReturn(Optional.of(proposal));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(currentUser));
        when(userRepository.findById(9L)).thenReturn(Optional.of(currentUser));

        ProposalDTO result = service.update(30L, updateRequest(999L));

        assertEquals(9L, proposal.getPreparedById());
        assertEquals("Güncel Hazırlayan", result.getPreparedByName());
    }

    private Proposal proposal(Long preparedById) {
        Proposal proposal = new Proposal();
        proposal.setId(30L);
        proposal.setCompanyId(10L);
        proposal.setCustomerId(20L);
        proposal.setPreparedById(preparedById);
        proposal.setStatus(Proposal.ProposalStatus.DRAFT);
        proposal.setTaxRate(new BigDecimal("20"));
        proposal.setDiscount(BigDecimal.ZERO);
        return proposal;
    }

    private ProposalDTO updateRequest(Long preparedById) {
        return ProposalDTO.builder()
                .customerId(20L)
                .preparedById(preparedById)
                .status("DRAFT")
                .taxRate(new BigDecimal("20"))
                .discount(BigDecimal.ZERO)
                .items(List.of())
                .build();
    }

    private User user(Long id, String username, String fullName) {
        return User.builder()
                .id(id)
                .companyId(10L)
                .username(username)
                .passwordHash("hash")
                .role("COMPANY_ADMIN")
                .fullName(fullName)
                .signaturePath("signatures/" + id + "/signature.png")
                .build();
    }
}
