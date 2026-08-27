package com.pusula.backend.service;

import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.Proposal;
import com.pusula.backend.entity.ProposalItem;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceUsedPart;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ProposalItemRepository;
import com.pusula.backend.repository.ProposalRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.ServiceUsedPartRepository;
import com.pusula.backend.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProposalInventoryConversionTest {

    @Mock private ProposalRepository proposalRepository;
    @Mock private ProposalItemRepository proposalItemRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private UserRepository userRepository;
    @Mock private ServiceTicketRepository serviceTicketRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ServiceUsedPartRepository serviceUsedPartRepository;
    @Mock private FeatureService featureService;

    private ProposalService service;
    private Proposal proposal;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        service = new ProposalService(proposalRepository, proposalItemRepository, customerRepository,
                userRepository, serviceTicketRepository, inventoryRepository, serviceUsedPartRepository,
                featureService);

        proposal = new Proposal();
        proposal.setId(16L);
        proposal.setCompanyId(10L);
        proposal.setCustomerId(20L);
        proposal.setStatus(Proposal.ProposalStatus.SENT);
        proposal.setTaxRate(new BigDecimal("20"));
        proposal.setDiscount(BigDecimal.ZERO);

        ProposalItem item = new ProposalItem();
        item.setCompanyId(10L);
        item.setProposal(proposal);
        item.setInventoryId(5L);
        item.setDescription("X parçası");
        item.setQuantity(2);
        item.setUnitCost(new BigDecimal("1200"));
        item.setUnitPrice(new BigDecimal("2500"));
        item.setTotalPrice(new BigDecimal("5000"));
        proposal.getItems().add(item);

        inventory = new Inventory(5L, 10L, "X parçası", 5,
                new BigDecimal("1200"), new BigDecimal("3000"), 1);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", "password"));
        User admin = User.builder().id(9L).companyId(10L).username("admin")
                .passwordHash("hash").role("COMPANY_ADMIN").fullName("Admin").build();

        when(proposalRepository.findByIdAndCompanyIdForUpdate(16L, 10L)).thenReturn(Optional.of(proposal));
        when(proposalRepository.save(any(Proposal.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.findByIdAndCompanyIdForUpdate(5L, 10L)).thenReturn(Optional.of(inventory));
        lenient().when(serviceTicketRepository.save(any(ServiceTicket.class))).thenAnswer(invocation -> {
            ServiceTicket ticket = invocation.getArgument(0);
            ticket.setId(101L);
            return ticket;
        });
        lenient().when(customerRepository.findById(20L))
                .thenReturn(Optional.of(Customer.builder().id(20L).companyId(10L).name("Müşteri").build()));
        lenient().when(userRepository.findByUsername("admin")).thenReturn(Optional.of(admin));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void conversionDeductsRealStockAndKeepsProposalSpecificSellingPrice() {
        service.convertToJob(16L, 10L);

        assertEquals(BigDecimal.valueOf(3), inventory.getQuantity());
        assertEquals(Proposal.ProposalStatus.APPROVED, proposal.getStatus());

        ArgumentCaptor<ServiceUsedPart> captor = ArgumentCaptor.forClass(ServiceUsedPart.class);
        verify(serviceUsedPartRepository).save(captor.capture());
        ServiceUsedPart usedPart = captor.getValue();
        assertEquals(BigDecimal.valueOf(2), usedPart.getQuantityUsed());
        assertEquals(new BigDecimal("2500"), usedPart.getSellingPriceSnapshot());
        assertEquals(new BigDecimal("1200"), usedPart.getBuyingPriceSnapshot());
        assertEquals(5L, usedPart.getInventory().getId());
    }

    @Test
    void conversionRejectsInsufficientStockBeforeCreatingTicket() {
        inventory.setQuantity(1);

        RuntimeException error = assertThrows(RuntimeException.class,
                () -> service.convertToJob(16L, 10L));

        assertEquals("X parçası için stok yetersiz. Gerekli: 2, mevcut: 1", error.getMessage());
        verify(serviceTicketRepository, never()).save(any());
        verify(serviceUsedPartRepository, never()).save(any());
    }
}
