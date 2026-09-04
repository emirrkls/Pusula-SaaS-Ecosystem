package com.pusula.backend.controller;

import com.pusula.backend.annotation.RequiresFeature;

import com.pusula.backend.dto.ServicePhotoDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.dto.ServiceUsedPartDTO;
import com.pusula.backend.dto.CompleteServiceRequest;
import com.pusula.backend.dto.BulkTicketAssignmentRequest;
import com.pusula.backend.dto.AddServiceTicketNoteRequest;
import com.pusula.backend.dto.ServiceTicketNoteDTO;
import com.pusula.backend.dto.ServiceTicketRescheduleRequest;
import com.pusula.backend.dto.AuthRequest;
import com.pusula.backend.entity.ServicePhoto;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.ServiceTicketService;
import com.pusula.backend.service.AuthenticationService;
import com.pusula.backend.service.ServiceTicketNoteService;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tickets")
@RequiresFeature("SERVICE_TICKETS")
public class ServiceTicketController {

    private final ServiceTicketService service;
    private final AuthenticationService authenticationService;
    private final ServiceTicketNoteService noteService;

    public ServiceTicketController(ServiceTicketService service, AuthenticationService authenticationService,
            ServiceTicketNoteService noteService) {
        this.service = service;
        this.authenticationService = authenticationService;
        this.noteService = noteService;
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

    @PatchMapping("/{id}/reschedule")
    public ResponseEntity<ServiceTicketDTO> rescheduleTicket(@PathVariable Long id,
            @RequestBody ServiceTicketRescheduleRequest request) {
        return ResponseEntity.ok(service.rescheduleTicket(id, request));
    }

    @PatchMapping("/{id}/resume")
    public ResponseEntity<ServiceTicketDTO> resumeTicket(@PathVariable Long id) {
        return ResponseEntity.ok(service.resumeTicket(id));
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ServiceTicketDTO> assignTechnician(@PathVariable Long id, @RequestParam Long technicianId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime scheduledEndDate) {
        return ResponseEntity.ok(service.assignTechnician(id, technicianId, scheduledDate, scheduledEndDate));
    }

    @PatchMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ServiceTicketDTO> reopenCompletedService(
            @PathVariable Long id, @RequestBody AuthRequest request) {
        if (!authenticationService.verifyCurrentUserPassword(request != null ? request.getPassword() : null)) {
            throw new AccessDeniedException("Yönetici şifresi doğrulanamadı.");
        }
        return ResponseEntity.ok(service.reopenCompletedService(id));
    }

    @PatchMapping("/bulk-assign")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<List<ServiceTicketDTO>> assignTechnicianBulk(
            @RequestBody BulkTicketAssignmentRequest request) {
        return ResponseEntity.ok(service.assignTechnicianBulk(request.getTicketIds(), request.getTechnicianId()));
    }

    @PostMapping("/{id}/parts")
    public ResponseEntity<ServiceUsedPartDTO> addUsedPart(@PathVariable Long id, @RequestBody ServiceUsedPartDTO dto) {
        return ResponseEntity.ok(service.addUsedPart(id, dto));
    }

    @GetMapping("/{id}/parts")
    public ResponseEntity<List<ServiceUsedPartDTO>> getUsedParts(@PathVariable Long id) {
        return ResponseEntity.ok(service.getUsedParts(id));
    }

    @GetMapping("/{id}/technician-notes")
    public ResponseEntity<List<ServiceTicketNoteDTO>> getTechnicianNotes(@PathVariable Long id) {
        return ResponseEntity.ok(noteService.getNotes(id));
    }

    @PostMapping("/{id}/technician-notes")
    public ResponseEntity<ServiceTicketNoteDTO> addTechnicianNote(@PathVariable Long id,
            @RequestBody AddServiceTicketNoteRequest request) {
        return ResponseEntity.ok(noteService.addWorkLog(id, request.getContent()));
    }

    @PutMapping("/{id}/parts/{partId}")
    public ResponseEntity<ServiceUsedPartDTO> updateUsedPart(
            @PathVariable Long id,
            @PathVariable Long partId,
            @RequestBody ServiceUsedPartDTO dto) {
        return ResponseEntity.ok(service.updateUsedPart(id, partId, dto));
    }

    @DeleteMapping("/{id}/parts/{partId}")
    public ResponseEntity<Void> deleteUsedPart(@PathVariable Long id, @PathVariable Long partId) {
        service.deleteUsedPart(id, partId);
        return ResponseEntity.noContent().build();
    }

    /**
     * PATCH /api/tickets/{id}/complete — complete service with separate sale and collection amounts.
     * Structured clients send laborFee; the server adds snapshotted part sales and
     * moves any unpaid remainder to the customer's current account.
     */
    @PatchMapping("/{id}/complete")
    @Transactional
    public ResponseEntity<ServiceTicketDTO> completeService(@PathVariable Long id,
            @RequestBody CompleteServiceRequest request) {
        ServiceTicketDTO completed = service.completeService(id, request.getCollectedAmount(),
                request.getLaborFee(), request.getPaymentMethod(), request.getCompletionDate());
        if (request.getTechnicianNote() != null && !request.getTechnicianNote().isBlank()) {
            noteService.addClosureNote(id, request.getTechnicianNote());
        }
        return ResponseEntity.ok(completed);
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
            @RequestParam(value = "note", required = false) String note,
            @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(service.uploadServicePhoto(id, type, note, file));
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
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(service.getCompanyServicePhotos(type, ticketId, startDate, endDate, query, limit));
    }

    @DeleteMapping("/{ticketId}/photos/{photoId}")
    public ResponseEntity<Void> deleteServicePhoto(
            @PathVariable Long ticketId,
            @PathVariable Long photoId) {
        service.deleteServicePhoto(ticketId, photoId);
        return ResponseEntity.noContent().build();
    }
}
