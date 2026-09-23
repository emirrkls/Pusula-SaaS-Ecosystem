package com.pusula.backend.service;

import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.AccountPartyRepository;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ProposalRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.annotation.CheckQuota;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final UserRepository userRepository;
    private final ServiceTicketRepository serviceTicketRepository;
    private final ProposalRepository proposalRepository;
    private final CurrentAccountRepository currentAccountRepository;
    private final AccountPartyRepository accountPartyRepository;
    private final AuditLogService auditLogService;

    public CustomerService(CustomerRepository customerRepository, UserRepository userRepository,
            ServiceTicketRepository serviceTicketRepository, ProposalRepository proposalRepository,
            CurrentAccountRepository currentAccountRepository, AccountPartyRepository accountPartyRepository,
            AuditLogService auditLogService) {
        this.customerRepository = customerRepository;
        this.userRepository = userRepository;
        this.serviceTicketRepository = serviceTicketRepository;
        this.proposalRepository = proposalRepository;
        this.currentAccountRepository = currentAccountRepository;
        this.accountPartyRepository = accountPartyRepository;
        this.auditLogService = auditLogService;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public List<Customer> getAllCustomers() {
        User user = getCurrentUser();
        return customerRepository.findByCompanyId(user.getCompanyId());
    }

    public Customer getCustomerById(Long id) {
        User user = getCurrentUser();
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        if (!customer.getCompanyId().equals(user.getCompanyId())) {
            throw new RuntimeException("Unauthorized access to customer");
        }

        return customer;
    }

    @CheckQuota("CUSTOMERS")
    public Customer createCustomer(Customer customer) {
        User user = getCurrentUser();
        validateCustomer(customer);
        customer.setCompanyId(user.getCompanyId());
        return customerRepository.save(customer);
    }

    @Transactional
    public Customer updateCustomer(Long id, Customer updatedCustomer) {
        User user = getCurrentUser();
        validateCustomer(updatedCustomer);
        Customer existing = customerRepository.findByIdAndCompanyId(id, user.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı veya erişim reddedildi."));

        existing.setName(updatedCustomer.getName().trim());
        existing.setPhone(clean(updatedCustomer.getPhone()));
        existing.setAddress(clean(updatedCustomer.getAddress()));
        existing.setCoordinates(updatedCustomer.getCoordinates());
        Customer saved = customerRepository.save(existing);

        accountPartyRepository.findByCompanyIdAndCustomerId(user.getCompanyId(), id).ifPresent(party -> {
            party.setDisplayName(saved.getName());
            party.setPhone(saved.getPhone());
            party.setAddress(saved.getAddress());
            accountPartyRepository.save(party);
        });
        auditLogService.log("UPDATE", "CUSTOMER", id, "Müşteri güncellendi: " + saved.getName());
        return saved;
    }

    @Transactional
    public void deleteCustomer(Long id) {
        User user = getCurrentUser();
        if (!isAdmin(user)) {
            throw new AccessDeniedException("Müşteri silme işlemi yalnızca işletme yöneticisi tarafından yapılabilir.");
        }
        Customer customer = customerRepository.findByIdAndCompanyId(id, user.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("Müşteri bulunamadı veya erişim reddedildi."));

        boolean hasHistory = serviceTicketRepository.existsByCompanyIdAndCustomerId(user.getCompanyId(), id)
                || proposalRepository.existsByCompanyIdAndCustomerId(user.getCompanyId(), id)
                || currentAccountRepository.findByCustomerIdAndCompanyId(id, user.getCompanyId()).isPresent();
        if (hasHistory) {
            throw new IllegalStateException(
                    "Geçmiş iş emri, teklif veya cari hareketi bulunan müşteri silinemez. Müşteri bilgilerini düzenleyebilirsiniz.");
        }

        accountPartyRepository.findByCompanyIdAndCustomerId(user.getCompanyId(), id).ifPresent(party -> {
            party.setActive(false);
            accountPartyRepository.save(party);
        });
        customerRepository.delete(customer);
        auditLogService.log("DELETE", "CUSTOMER", id, "Müşteri silindi: " + customer.getName());
    }

    private boolean isAdmin(User user) {
        return "COMPANY_ADMIN".equals(user.getRole()) || "SUPER_ADMIN".equals(user.getRole());
    }

    private void validateCustomer(Customer customer) {
        if (customer == null || customer.getName() == null || customer.getName().isBlank()) {
            throw new IllegalArgumentException("Müşteri adı zorunludur.");
        }
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
