package com.pusula.backend.controller;

import com.pusula.backend.entity.Customer;
import com.pusula.backend.dto.CustomerWhatsAppConsentDTO;
import com.pusula.backend.dto.UpdateCustomerWhatsAppConsentRequest;
import com.pusula.backend.service.CustomerService;
import com.pusula.backend.annotation.RequiresFeature;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiresFeature("CUSTOMER_MANAGEMENT")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ResponseEntity<List<Customer>> getAllCustomers() {
        return ResponseEntity.ok(customerService.getAllCustomers());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Customer> getCustomerById(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getCustomerById(id));
    }

    @PostMapping
    public ResponseEntity<Customer> createCustomer(@RequestBody Customer customer) {
        return ResponseEntity.ok(customerService.createCustomer(customer));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @RequestBody Customer customer) {
        return ResponseEntity.ok(customerService.updateCustomer(id, customer));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/whatsapp-consent")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CustomerWhatsAppConsentDTO> getWhatsAppConsent(@PathVariable Long id) {
        return ResponseEntity.ok(customerService.getWhatsAppConsent(id));
    }

    @PutMapping("/{id}/whatsapp-consent")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<CustomerWhatsAppConsentDTO> updateWhatsAppConsent(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCustomerWhatsAppConsentRequest request) {
        return ResponseEntity.ok(customerService.updateWhatsAppConsent(id, request));
    }
}
