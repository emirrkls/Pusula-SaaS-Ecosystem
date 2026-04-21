package com.pusula.backend.controller;

import com.pusula.backend.dto.ServiceTicketExpenseDTO;
import com.pusula.backend.service.ServiceTicketExpenseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/service-tickets")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ServiceTicketExpenseController {

    private final ServiceTicketExpenseService expenseService;

    /**
     * Get all external expenses for a service ticket
     */
    @GetMapping("/{ticketId}/expenses")
    public ResponseEntity<List<ServiceTicketExpenseDTO>> getExpenses(@PathVariable Long ticketId) {
        return ResponseEntity.ok(expenseService.getExpensesForTicket(ticketId));
    }

    /**
     * Add an external expense to a service ticket
     */
    @PostMapping("/{ticketId}/expenses")
    public ResponseEntity<ServiceTicketExpenseDTO> addExpense(
            @PathVariable Long ticketId,
            @RequestBody ServiceTicketExpenseDTO dto) {
        dto.setServiceTicketId(ticketId);
        if (dto.getCompanyId() == null) {
            dto.setCompanyId(1L);
        }
        return ResponseEntity.ok(expenseService.addExpense(dto));
    }

    /**
     * Delete an expense
     */
    @DeleteMapping("/{ticketId}/expenses/{expenseId}")
    public ResponseEntity<Void> deleteExpense(
            @PathVariable Long ticketId,
            @PathVariable Long expenseId) {
        expenseService.deleteExpense(expenseId);
        return ResponseEntity.ok().build();
    }
}
