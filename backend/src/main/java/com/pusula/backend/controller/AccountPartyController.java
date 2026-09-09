package com.pusula.backend.controller;

import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.dto.AccountPartyDTO;
import com.pusula.backend.entity.AccountParty;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.AccountPartyService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import com.pusula.backend.dto.AccountPartyOptionDTO;

@RestController
@RequestMapping("/api/account-parties")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
@RequiresFeature("FINANCE_MODULE")
public class AccountPartyController {
    private final AccountPartyService service;

    public AccountPartyController(AccountPartyService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public List<AccountPartyDTO> list(@RequestParam(required = false) AccountParty.PartyType type) {
        return service.list(companyId(), type);
    }

    @GetMapping("/billing-options")
    public List<AccountPartyOptionDTO> billingOptions() {
        return service.list(companyId(), AccountParty.PartyType.ORGANIZATION).stream()
                .map(row -> new AccountPartyOptionDTO(row.getId(), row.getDisplayName(), row.getPartyType().name()))
                .toList();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public AccountPartyDTO create(@RequestBody AccountPartyDTO request) {
        return service.create(companyId(), request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public AccountPartyDTO update(@PathVariable Long id, @RequestBody AccountPartyDTO request) {
        return service.update(companyId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        service.deactivate(companyId(), id);
        return ResponseEntity.noContent().build();
    }

    private Long companyId() {
        return ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
    }
}
