package com.pusula.backend.controller;

import com.pusula.backend.dto.CurrentAccountDTO;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/current-accounts")
public class CurrentAccountController {

    @Autowired
    private CurrentAccountRepository currentAccountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServiceTicketRepository serviceTicketRepository;

    @GetMapping
    public List<CurrentAccountDTO> getAll(@RequestHeader("X-Company-Id") Long companyId) {
        return currentAccountRepository.findByCompanyIdOrderByBalanceDesc(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<CurrentAccount> getByCustomer(@PathVariable Long customerId) {
        return currentAccountRepository.findByCustomerId(customerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> createOrUpdate(@RequestHeader("X-Company-Id") Long companyId,
            @RequestBody Map<String, Object> request) {
        Long customerId = ((Number) request.get("customerId")).longValue();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return ResponseEntity.badRequest().body("Customer not found");
        }

        CurrentAccount account = currentAccountRepository.findByCustomerId(customerId)
                .orElse(CurrentAccount.builder()
                        .companyId(companyId)
                        .customer(customer)
                        .balance(BigDecimal.ZERO)
                        .build());

        account.setBalance(account.getBalance().add(amount));
        return ResponseEntity.ok(currentAccountRepository.save(account));
    }

    @PutMapping("/{id}/adjust")
    public ResponseEntity<CurrentAccount> adjustBalance(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        return currentAccountRepository.findById(id)
                .map(account -> {
                    BigDecimal amount = new BigDecimal(request.get("amount").toString());
                    account.setBalance(account.getBalance().add(amount));
                    return ResponseEntity.ok(currentAccountRepository.save(account));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/set")
    public ResponseEntity<CurrentAccount> setBalance(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        return currentAccountRepository.findById(id)
                .map(account -> {
                    BigDecimal balance = new BigDecimal(request.get("balance").toString());
                    account.setBalance(balance);
                    return ResponseEntity.ok(currentAccountRepository.save(account));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Pay off current account debt with optional discount
     * Creates a ServiceTicket to record the payment as income
     * Request body: { "paymentAmount": 1000.00, "discount": 50.00 }
     */
    @PostMapping("/{id}/pay")
    public ResponseEntity<CurrentAccountDTO> payDebt(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        return currentAccountRepository.findById(id)
                .map(account -> {
                    BigDecimal paymentAmount = new BigDecimal(request.get("paymentAmount").toString());
                    BigDecimal discount = request.containsKey("discount")
                            ? new BigDecimal(request.get("discount").toString())
                            : BigDecimal.ZERO;

                    // Apply payment and discount: reduce debt by payment + discount
                    BigDecimal totalReduction = paymentAmount.add(discount);
                    account.setBalance(account.getBalance().subtract(totalReduction));

                    // Create a ServiceTicket to record payment as income
                    // Only if paymentAmount > 0 (actual money received)
                    if (paymentAmount.compareTo(BigDecimal.ZERO) > 0) {
                        String customerName = account.getCustomer() != null
                                ? account.getCustomer().getName()
                                : "Bilinmeyen Müşteri";

                        ServiceTicket incomeTicket = ServiceTicket.builder()
                                .companyId(account.getCompanyId())
                                .customerId(account.getCustomer() != null ? account.getCustomer().getId() : null)
                                .status(ServiceTicket.TicketStatus.COMPLETED)
                                .description("Cari hesap ödemesi - " + customerName)
                                .collectedAmount(paymentAmount)
                                .build();
                        // PaymentMethod defaults to CASH

                        serviceTicketRepository.save(incomeTicket);
                    }

                    return ResponseEntity.ok(mapToDTO(currentAccountRepository.save(account)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private CurrentAccountDTO mapToDTO(CurrentAccount account) {
        String customerName = account.getCustomer() != null
                ? account.getCustomer().getName()
                : "Unknown";

        return CurrentAccountDTO.builder()
                .id(account.getId())
                .companyId(account.getCompanyId())
                .customerId(account.getCustomer() != null ? account.getCustomer().getId() : null)
                .customerName(customerName)
                .balance(account.getBalance())
                .lastUpdated(account.getLastUpdated())
                .build();
    }
}
