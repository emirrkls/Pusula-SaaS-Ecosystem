package com.pusula.backend.controller;

import com.pusula.backend.dto.ServicePhotoDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.entity.ServicePhoto;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.ServiceTicketService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
public class ServiceTicketController {

    private final ServiceTicketService service;

    public ServiceTicketController(ServiceTicketService service) {
        this.service = service;
    }

    private User getCurrentUser() {
        return (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /**
     * GET /api/tickets — all tickets for the company (admins) or assigned tickets (technicians).
     */
    @GetMapping
    public ResponseEntity<List<ServiceTicketDTO>> getAllTickets() {
        return ResponseEntity.ok(service.getAllTickets());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceTicketDTO> getTicketById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTicketById(id));
    }

    /**
     * GET /api/tickets/my-assigned — technician's assigned tickets only.
     * Enriched with customer address, phone, and outstanding balance.
     */
    @GetMapping("/my-assigned")
    public ResponseEntity<List<ServiceTicketDTO>> getMyAssignedTickets() {
        User user = getCurrentUser();
        return ResponseEntity.ok(service.getAssignedTickets(user.getId()));
    }

    @PostMapping
    public ResponseEntity<ServiceTicketDTO> createTicket(@RequestBody ServiceTicketDTO dto) {
        return ResponseEntity.ok(service.createTicket(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceTicketDTO> updateTicket(@PathVariable Long id, @RequestBody ServiceTicketDTO dto) {
        return ResponseEntity.ok(service.updateTicket(id, dto));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ServiceTicketDTO> assignTechnician(@PathVariable Long id, @RequestParam Long technicianId) {
        return ResponseEntity.ok(service.assignTechnician(id, technicianId));
    }

    @PostMapping("/{id}/parts")
    public ResponseEntity<ServiceUsedPartDTO> addUsedPart(@PathVariable Long id, @RequestBody ServiceUsedPartDTO dto) {
        return ResponseEntity.ok(service.addUsedPart(id, dto));
    }

    @GetMapping("/{id}/parts")
    public ResponseEntity<List<ServiceUsedPartDTO>> getUsedParts(@PathVariable Long id) {
        return ResponseEntity.ok(service.getUsedParts(id));
    }

    /**
     * PATCH /api/tickets/{id}/complete — complete service with waterfall payment model.
     * Supports: collectedAmount, paymentMethod, remainingToDebt (cari)
     */
    @PatchMapping("/{id}/complete")
    public ResponseEntity<ServiceTicketDTO> completeService(@PathVariable Long id,
            @RequestBody Map<String, Object> request) {
        BigDecimal amount = new BigDecimal(request.get("collectedAmount").toString());
        com.pusula.backend.entity.PaymentMethod paymentMethod = request.containsKey("paymentMethod")
                ? com.pusula.backend.entity.PaymentMethod.valueOf(request.get("paymentMethod").toString())
                : com.pusula.backend.entity.PaymentMethod.CASH;

        return ResponseEntity.ok(service.completeService(id, amount, paymentMethod));
    }

    @PatchMapping("/{id}/cancel")
    public ResponseEntity<ServiceTicketDTO> cancelService(@PathVariable Long id) {
        return ResponseEntity.ok(service.cancelService(id));
    }

    @PostMapping("/{id}/follow-up")
    public ResponseEntity<ServiceTicketDTO> createFollowUp(@PathVariable Long id) {
        return ResponseEntity.ok(service.createFollowUpTicket(id));
    }

    /**
     * POST /api/tickets/{id}/signature — upload signature image (base64).
     * Stores locally: /uploads/signatures/{companyId}/{ticketId}.png
     */
    @PostMapping("/{id}/signature")
    public ResponseEntity<Map<String, String>> uploadSignature(
            @PathVariable Long id,
            @RequestBody Map<String, String> request) {
        String signatureBase64 = request.get("signature");
        String path = service.saveSignature(id, signatureBase64);
        return ResponseEntity.ok(Map.of("path", path));
    }

    @PostMapping("/{id}/photos")
    public ResponseEntity<ServicePhotoDTO> uploadServicePhoto(
            @PathVariable Long id,
            @RequestParam("type") ServicePhoto.PhotoType type,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.uploadServicePhoto(id, type, file));
    }

    @GetMapping("/{id}/photos")
    public ResponseEntity<List<ServicePhotoDTO>> getServicePhotos(@PathVariable Long id) {
        return ResponseEntity.ok(service.getServicePhotos(id));
    }

    @GetMapping("/photos")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ServicePhotoDTO>> getCompanyServicePhotos(
            @RequestParam(value = "type", required = false) ServicePhoto.PhotoType type,
            @RequestParam(value = "ticketId", required = false) Long ticketId,
            @RequestParam(value = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(service.getCompanyServicePhotos(type, ticketId, startDate, endDate, limit));
    }

    @DeleteMapping("/{ticketId}/photos/{photoId}")
    public ResponseEntity<Void> deleteServicePhoto(
            @PathVariable Long ticketId,
            @PathVariable Long photoId) {
        service.deleteServicePhoto(ticketId, photoId);
        return ResponseEntity.noContent().build();
    }
}
