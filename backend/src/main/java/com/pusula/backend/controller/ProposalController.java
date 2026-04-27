package com.pusula.backend.controller;

import com.pusula.backend.dto.ProposalDTO;
import com.pusula.backend.service.ProposalService;
import com.pusula.backend.service.ReportService;
import com.pusula.backend.entity.User;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/proposals")
@PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN', 'TECHNICIAN')")
public class ProposalController {

    private final ProposalService proposalService;
    private final ReportService reportService;

    public ProposalController(ProposalService proposalService, ReportService reportService) {
        this.proposalService = proposalService;
        this.reportService = reportService;
    }

    @GetMapping
    public List<ProposalDTO> getAllProposals() {
        User user = getCurrentUser();
        return proposalService.getAllByCompany(user.getCompanyId());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProposalDTO> getById(@PathVariable Long id) {
        ProposalDTO dto = proposalService.getById(id);
        if (dto == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping
    public ResponseEntity<ProposalDTO> create(@RequestBody ProposalDTO dto) {
        ProposalDTO created = proposalService.create(dto);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProposalDTO> update(@PathVariable Long id, @RequestBody ProposalDTO dto) {
        try {
            ProposalDTO updated = proposalService.update(id, dto);
            return ResponseEntity.ok(updated);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        proposalService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ProposalDTO> convertToJob(@PathVariable Long id) {
        try {
            ProposalDTO result = proposalService.convertToJob(id);
            return ResponseEntity.ok(result);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> generatePdf(@PathVariable Long id) {
        try {
            byte[] pdfBytes = reportService.generateProposalForm(id);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "teklif-" + id + ".pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
