package com.pusula.backend.controller;

import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.UserRepository;
import com.pusula.backend.service.ReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/reports")
@CrossOrigin(origins = "*")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ServiceTicketRepository ticketRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;

    public ReportController(ServiceTicketRepository ticketRepository,
            UserRepository userRepository,
            ReportService reportService) {
        this.ticketRepository = ticketRepository;
        this.userRepository = userRepository;
        this.reportService = reportService;
    }

    private Long getCompanyId() {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return user.getCompanyId();
    }

    /**
     * Same rules as {@link com.pusula.backend.service.ServiceTicketService#getTicketById}: company scope;
     * technicians only for tickets assigned to them.
     */
    private void assertCanAccessServiceReportPdf(Long ticketId) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        String role = user.getRole();
        boolean allowedRole = "COMPANY_ADMIN".equals(role) || "SUPER_ADMIN".equals(role) || "TECHNICIAN".equals(role);
        if (!allowedRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
        }
        ServiceTicket ticket = ticketRepository.findById(ticketId)
                .filter(t -> t.getCompanyId().equals(user.getCompanyId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
        if ("TECHNICIAN".equals(role)) {
            if (ticket.getAssignedTechnicianId() == null
                    || !ticket.getAssignedTechnicianId().equals(user.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        }
    }

    /**
     * Get technician performance for the last 7 days
     * Returns a map of technician name -> daily completion counts
     */
    @GetMapping("/technician-performance")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Map<String, Map<String, Integer>>> getTechnicianPerformance() {
        // Get completed tickets from last 7 days
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<ServiceTicket> completedTickets = ticketRepository.findCompletedTicketsSince(sevenDaysAgo);

        // DateTimeFormatter for date keys
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        // Group by technician and date
        Map<String, Map<String, Integer>> performanceData = new HashMap<>();

        for (ServiceTicket ticket : completedTickets) {
            if (ticket.getAssignedTechnicianId() == null || ticket.getUpdatedAt() == null) {
                continue;
            }

            // Get technician name
            User technician = userRepository.findById(ticket.getAssignedTechnicianId()).orElse(null);
            if (technician == null) {
                continue;
            }
            String techName = technician.getFullName();

            // Get date string
            String dateKey = ticket.getUpdatedAt().toLocalDate().format(dateFormatter);

            // Initialize maps if needed
            performanceData.putIfAbsent(techName, new HashMap<>());
            Map<String, Integer> dailyCounts = performanceData.get(techName);

            // Increment count
            dailyCounts.put(dateKey, dailyCounts.getOrDefault(dateKey, 0) + 1);
        }

        return ResponseEntity.ok(performanceData);
    }

    /**
     * Download Service Report PDF
     */
    @GetMapping("/pdf/service/{ticketId}")
    public ResponseEntity<byte[]> downloadServiceReport(@PathVariable Long ticketId) {
        try {
            assertCanAccessServiceReportPdf(ticketId);
            byte[] pdfBytes = reportService.generateServiceReport(ticketId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "servis_raporu_" + ticketId + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).build();
        } catch (Exception e) {
            log.error("downloadServiceReport failed for ticketId={}", ticketId, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Download Proposal PDF
     */
    @GetMapping("/pdf/proposal/{proposalId}")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadProposal(@PathVariable Long proposalId) {
        try {
            byte[] pdfBytes = reportService.generateProposalForm(proposalId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "teklif_" + proposalId + ".pdf");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get list of monthly financial archives
     */
    @GetMapping("/finance/archives")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public List<com.pusula.backend.dto.MonthlySummaryDTO> getMonthlyArchives() {
        return reportService.getMonthlyArchives(getCompanyId());
    }

    /**
     * Download monthly financial report as PDF
     */
    @GetMapping("/finance/pdf")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<byte[]> downloadMonthlyPDF(
            @RequestParam String month) { // Format: "2025-11"
        try {
            java.time.YearMonth period = java.time.YearMonth.parse(month);
            byte[] pdfBytes = reportService.generateMonthlyPDF(period, getCompanyId());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDispositionFormData("attachment", "mali_rapor_" + month + ".pdf");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdfBytes);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
