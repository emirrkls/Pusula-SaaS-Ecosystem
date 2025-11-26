package com.pusula.backend.controller;

import com.pusula.backend.entity.Company;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CompanyRepository;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
public class CompanyController {

    private final CompanyRepository companyRepository;

    public CompanyController(CompanyRepository companyRepository) {
        this.companyRepository = companyRepository;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * Get current user's company information
     */
    @GetMapping("/me")
    public ResponseEntity<Company> getMyCompany() {
        User currentUser = getCurrentUser();
        Company company = companyRepository.findById(currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));
        return ResponseEntity.ok(company);
    }

    /**
     * Update current user's company information
     */
    @PutMapping("/me")
    public ResponseEntity<Company> updateMyCompany(@RequestBody Company companyData) {
        User currentUser = getCurrentUser();
        Company existing = companyRepository.findById(currentUser.getCompanyId())
                .orElseThrow(() -> new RuntimeException("Company not found"));

        // Update editable fields
        existing.setName(companyData.getName());
        existing.setPhone(companyData.getPhone());
        existing.setAddress(companyData.getAddress());
        existing.setEmail(companyData.getEmail());

        Company updated = companyRepository.save(existing);
        return ResponseEntity.ok(updated);
    }
}
