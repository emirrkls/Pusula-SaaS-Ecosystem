package com.pusula.backend.controller;

import com.pusula.backend.dto.CurrentAccountDTO;
import com.pusula.backend.dto.CurrentAccountHistoryDTO;
import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.entity.CurrentAccount;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.CurrentAccountTransaction;
import com.pusula.backend.repository.CurrentAccountRepository;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.service.CurrentAccountLedgerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/current-accounts")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
@RequiresFeature("FINANCE_MODULE")
public class CurrentAccountController {

    @Autowired
    private CurrentAccountRepository currentAccountRepository;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ServiceTicketRepository serviceTicketRepository;

    @Autowired
    private CurrentAccountLedgerService ledgerService;

    @GetMapping
    public List<CurrentAccountDTO> getAll() {
        Long companyId = ((com.pusula.backend.entity.User) org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        return currentAccountRepository.findByCompanyIdOrderByBalanceDesc(companyId).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<CurrentAccount> getByCustomer(@PathVariable Long customerId) {
        return currentAccountRepository.findByCustomerIdAndCompanyId(customerId, getCompanyId())
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<CurrentAccountHistoryDTO> getHistory(@PathVariable Long id) {
        return ResponseEntity.ok(ledgerService.getHistory(id, getCompanyId()));
    }

    @PostMapping
    @Transactional
    public ResponseEntity<?> createOrUpdate(@RequestBody Map<String, Object> request) {
        Long companyId = getCompanyId();
        Long customerId = ((Number) request.get("customerId")).longValue();
        BigDecimal amount = new BigDecimal(request.get("amount").toString());

        validateNonNegative(amount, "Cari tutar");
        Customer customer = customerRepository.findByIdAndCompanyId(customerId, companyId).orElse(null);
        if (customer == null) {
            return ResponseEntity.badRequest().body("Customer not found");
        }

        CurrentAccount account = currentAccountRepository.findByCustomerIdAndCompanyId(customerId, companyId)
                .orElse(CurrentAccount.builder()
                        .companyId(companyId)
                        .customer(customer)
                        .balance(BigDecimal.ZERO)
                        .build());

        account.setBalance(account.getBalance().add(amount));
        CurrentAccount saved = currentAccountRepository.save(account);
        ledgerService.record(saved, CurrentAccountTransaction.TransactionType.ADJUSTMENT, amount,
                LocalDate.now(), "Manuel cari borç ilavesi", null, null, null);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/adjust")
    @Transactional
    public ResponseEntity<CurrentAccount> adjustBalance(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Long companyId = getCompanyId();
        return currentAccountRepository.findByIdAndCompanyId(id, companyId)
                .map(account -> {
                    BigDecimal amount = new BigDecimal(request.get("amount").toString());
                    BigDecimal newBalance = account.getBalance().add(amount);
                    validateNonNegative(newBalance, "Cari bakiye");
                    account.setBalance(newBalance);
                    CurrentAccount saved = currentAccountRepository.save(account);
                    ledgerService.record(saved, CurrentAccountTransaction.TransactionType.ADJUSTMENT, amount,
                            LocalDate.now(), "Manuel cari bakiye düzeltmesi", null, null, null);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/set")
    @Transactional
    public ResponseEntity<CurrentAccount> setBalance(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Long companyId = getCompanyId();
        return currentAccountRepository.findByIdAndCompanyId(id, companyId)
                .map(account -> {
                    BigDecimal balance = new BigDecimal(request.get("balance").toString());
                    validateNonNegative(balance, "Cari bakiye");
                    BigDecimal delta = balance.subtract(account.getBalance());
                    account.setBalance(balance);
                    CurrentAccount saved = currentAccountRepository.save(account);
                    ledgerService.record(saved, CurrentAccountTransaction.TransactionType.ADJUSTMENT, delta,
                            LocalDate.now(), "Cari bakiye doğrudan güncellendi", null, null, null);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Pay off current account debt with optional discount
     * Creates a ServiceTicket to record the payment as income
     * Request body: { "paymentAmount": 1000.00, "discount": 50.00,
     * "collectionDate": "2026-08-24", "paymentMethod": "CASH", "notes": "..." }
     */
    @PostMapping("/{id}/pay")
    @Transactional
    public ResponseEntity<CurrentAccountDTO> payDebt(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        Long companyId = getCompanyId();
        return currentAccountRepository.findByIdAndCompanyId(id, companyId)
                .map(account -> {
                    BigDecimal paymentAmount = new BigDecimal(request.get("paymentAmount").toString());
                    BigDecimal discount = request.containsKey("discount")
                            ? new BigDecimal(request.get("discount").toString())
                            : BigDecimal.ZERO;
                    LocalDate collectionDate = request.containsKey("collectionDate")
                            ? LocalDate.parse(request.get("collectionDate").toString())
                            : LocalDate.now();
                    PaymentMethod paymentMethod = request.containsKey("paymentMethod")
                            ? PaymentMethod.valueOf(request.get("paymentMethod").toString())
                            : PaymentMethod.CASH;
                    String notes = request.containsKey("notes") && request.get("notes") != null
                            ? request.get("notes").toString().trim()
                            : "";

                    if (paymentMethod != PaymentMethod.CASH && paymentMethod != PaymentMethod.CREDIT_CARD) {
                        throw new IllegalArgumentException("Cari tahsilat ödeme yöntemi nakit veya kart olmalıdır.");
                    }

                    validateNonNegative(paymentAmount, "Ödeme tutarı");
                    validateNonNegative(discount, "İndirim");

                    // Apply payment and discount: reduce debt by payment + discount
                    BigDecimal totalReduction = paymentAmount.add(discount);
                    if (totalReduction.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new IllegalArgumentException("Ödeme veya indirim tutarı sıfırdan büyük olmalıdır.");
                    }
                    if (totalReduction.compareTo(account.getBalance()) > 0) {
                        throw new IllegalArgumentException("Ödeme ve indirim toplamı cari bakiyeyi aşamaz.");
                    }
                    account.setBalance(account.getBalance().subtract(totalReduction));

                    // Create a ServiceTicket to record payment as income
                    // Only if paymentAmount > 0 (actual money received)
                    ServiceTicket incomeTicket = null;
                    if (paymentAmount.compareTo(BigDecimal.ZERO) > 0) {
                        String customerName = account.getCustomer() != null
                                ? account.getCustomer().getName()
                                : "Bilinmeyen Müşteri";

                        incomeTicket = ServiceTicket.builder()
                                .companyId(account.getCompanyId())
                                .customerId(account.getCustomer() != null ? account.getCustomer().getId() : null)
                                .status(ServiceTicket.TicketStatus.COMPLETED)
                                .description("Cari hesap ödemesi - " + customerName
                                        + (notes.isBlank() ? "" : " - " + notes))
                                .collectedAmount(paymentAmount)
                                .build();
                        incomeTicket.setPaymentMethod(paymentMethod);
                        incomeTicket.setCurrentAccountPayment(true);
                        incomeTicket.setCompletedAt(collectionDate.atTime(12, 0));
                        incomeTicket.setCollectionDate(collectionDate);

                        incomeTicket = serviceTicketRepository.save(incomeTicket);
                    }

                    CurrentAccount saved = currentAccountRepository.save(account);
                    if (paymentAmount.signum() > 0) {
                        ledgerService.record(saved, CurrentAccountTransaction.TransactionType.PAYMENT,
                                paymentAmount.negate(), collectionDate,
                                notes.isBlank() ? "Cari hesap tahsilatı" : "Cari hesap tahsilatı - " + notes,
                                paymentMethod, "CURRENT_ACCOUNT_PAYMENT",
                                incomeTicket != null ? incomeTicket.getId() : null);
                    }
                    if (discount.signum() > 0) {
                        ledgerService.record(saved, CurrentAccountTransaction.TransactionType.DISCOUNT,
                                discount.negate(), collectionDate,
                                notes.isBlank() ? "Cari hesap indirimi" : "Cari hesap indirimi - " + notes,
                                null, null, null);
                    }
                    return ResponseEntity.ok(mapToDTO(saved));
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

    private Long getCompanyId() {
        return ((com.pusula.backend.entity.User) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal()).getCompanyId();
    }

    private void validateNonNegative(BigDecimal amount, String label) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException(label + " negatif olamaz.");
        }
    }
}
