package com.pusula.backend.controller;

import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.dto.*;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.repository.ExpenseRepository;
import com.pusula.backend.service.*;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Map;

/** Versioned write contract: an older server cannot silently ignore a retry key. */
@RestController
@RequestMapping("/api/reliable-mutations")
public class ReliableMutationController {
    private final ReliableMutationService reliable;
    private final CustomerService customers;
    private final ServiceTicketService tickets;
    private final InventoryService inventory;
    private final InventoryRepository inventoryRepository;
    private final FinanceService finance;
    private final ExpenseRepository expenses;
    @org.springframework.beans.factory.annotation.Autowired
    private ServiceTicketNoteService notes;
    public ReliableMutationController(ReliableMutationService reliable, CustomerService customers,
            ServiceTicketService tickets, InventoryService inventory, InventoryRepository inventoryRepository,
            FinanceService finance, ExpenseRepository expenses) {
        this.reliable = reliable; this.customers = customers; this.tickets = tickets;
        this.inventory = inventory; this.inventoryRepository = inventoryRepository;
        this.finance = finance; this.expenses = expenses;
    }

    @PostMapping("/expenses") @RequiresFeature("FINANCE_MODULE")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Expense> createExpense(@RequestHeader("Idempotency-Key") String key,
                                                @Valid @RequestBody ReliableExpenseCreateRequest input) {
        Long companyId = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        return ResponseEntity.ok(reliable.execute(key, "expense.create", input,
                () -> finance.addExpense(input.toExpense(companyId)), Expense::getId,
                id -> expenses.findByIdAndCompanyId(id, companyId).orElse(null)));
    }

    @PostMapping("/customers") @RequiresFeature("CUSTOMER_MANAGEMENT")
    public ResponseEntity<Customer> createCustomer(@RequestHeader("Idempotency-Key") String key,
                                                   @RequestBody Customer input) {
        return ResponseEntity.ok(reliable.execute(key, "customer.create", input,
                () -> customers.createCustomer(input), Customer::getId, customers::getCustomerById));
    }

    @PostMapping("/tickets") @RequiresFeature("SERVICE_TICKETS")
    public ResponseEntity<ServiceTicketDTO> createTicket(@RequestHeader("Idempotency-Key") String key,
                                                         @RequestBody ServiceTicketDTO input) {
        return ResponseEntity.ok(reliable.execute(key, "ticket.create", input,
                () -> tickets.createTicket(input), ServiceTicketDTO::getId, tickets::getTicketById));
    }

    @PostMapping("/tickets/{ticketId}/complete") @RequiresFeature("SERVICE_TICKETS")
    public ResponseEntity<ServiceTicketDTO> completeTicket(@PathVariable Long ticketId,
            @RequestHeader("Idempotency-Key") String key, @RequestBody CompleteServiceRequest input) {
        // Include the ticket ID in the fingerprint: a key may not close another ticket.
        tickets.getTicketById(ticketId);
        return ResponseEntity.ok(reliable.execute(key, "ticket.complete", Map.of("ticketId", ticketId, "request", input),
                () -> {
                    ServiceTicketDTO completed = tickets.completeService(ticketId, input.getCollectedAmount(),
                            input.getLaborFee(), input.getPaymentMethod(), input.getCompletionDate(),
                            input.getBillingPartyId(), input.getBillingResponsibility(), input.getBillingPartyAmount(),
                            input.getDiscountType(), input.getDiscountValue(), input.getExpectedPartsTotal());
                    if (input.getTechnicianNote() != null && !input.getTechnicianNote().isBlank()) {
                        notes.addClosureNote(ticketId, input.getTechnicianNote());
                    }
                    return completed;
                }, ServiceTicketDTO::getId, id -> {
                    ServiceTicketDTO result = tickets.getTicketById(id);
                    // A historic receipt must not claim a reopened ticket was just completed.
                    return result != null && result.getStatus() == ServiceTicket.TicketStatus.COMPLETED ? result : null;
                }));
    }

    @PostMapping("/inventory") @RequiresFeature("BASIC_INVENTORY")
    @PreAuthorize("hasAnyRole('COMPANY_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<InventoryDTO> createInventory(@RequestHeader("Idempotency-Key") String key,
                                                        @Valid @RequestBody InventoryDTO input) {
        Long companyId = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getCompanyId();
        return ResponseEntity.ok(reliable.execute(key, "inventory.create", input,
                () -> inventory.createInventory(input), InventoryDTO::getId,
                id -> inventoryRepository.findByIdAndCompanyId(id, companyId).map(inventory::mapToFullDTO).orElse(null)));
    }

    @PostMapping("/tickets/{ticketId}/photos") @RequiresFeature("SERVICE_TICKETS")
    public ResponseEntity<ServicePhotoDTO> uploadPhoto(@PathVariable Long ticketId,
            @RequestHeader("Idempotency-Key") String key, @RequestParam ServicePhoto.PhotoType type,
            @RequestParam(required = false) String note, @RequestParam("file") MultipartFile file) {
        // Re-check ticket ownership/assignment on both first attempts and replays.
        tickets.getTicketById(ticketId);
        Map<String, Object> input = Map.of("ticketId", ticketId, "type", type.name(),
                "note", note == null ? "" : note, "fileSha256", reliable.fileFingerprint(file));
        return ResponseEntity.ok(reliable.execute(key, "ticket.photo.create", input,
                () -> tickets.uploadServicePhoto(ticketId, type, note, file), ServicePhotoDTO::getId,
                id -> tickets.getServicePhotos(ticketId).stream().filter(photo -> photo.getId().equals(id)).findFirst().orElse(null)));
    }

    @PostMapping("/tickets/{ticketId}/technician-notes") @RequiresFeature("SERVICE_TICKETS")
    public ResponseEntity<ServiceTicketNoteDTO> addTechnicianNote(@PathVariable Long ticketId,
            @RequestHeader("Idempotency-Key") String key, @Valid @RequestBody AddServiceTicketNoteRequest input) {
        notes.checkTicketAccess(ticketId);
        String content = input.getContent().trim();
        // Bind the receipt to the target, normalized text and notification intent.
        Map<String, Object> fingerprint = Map.of("ticketId", ticketId, "content", content,
                "important", input.isImportant());
        return ResponseEntity.ok(reliable.execute(key, "ticket.note.create", fingerprint,
                () -> notes.addWorkLog(ticketId, content, input.isImportant()), ServiceTicketNoteDTO::getId,
                id -> notes.getNoteForReplay(ticketId, id)));
    }
}
