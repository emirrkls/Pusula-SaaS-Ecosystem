package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketExpenseDTO;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ServiceTicketExpense;
import com.pusula.backend.repository.ExpenseRepository;
import com.pusula.backend.repository.ServiceTicketExpenseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ServiceTicketExpenseService {

    private final ServiceTicketExpenseRepository repository;
    private final ExpenseRepository expenseRepository;
    private final AuditLogService auditLogService;

    /**
     * Get all expenses for a service ticket
     */
    public List<ServiceTicketExpenseDTO> getExpensesForTicket(Long ticketId) {
        return repository.findByServiceTicketId(ticketId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Add an external expense to a service ticket
     * Also creates an Expense record so it shows in finance
     */
    @Transactional
    public ServiceTicketExpenseDTO addExpense(ServiceTicketExpenseDTO dto) {
        // Create service ticket expense record
        ServiceTicketExpense expense = ServiceTicketExpense.builder()
                .serviceTicketId(dto.getServiceTicketId())
                .companyId(dto.getCompanyId())
                .description(dto.getDescription())
                .amount(dto.getAmount())
                .supplier(dto.getSupplier())
                .notes(dto.getNotes())
                .build();

        ServiceTicketExpense saved = repository.save(expense);

        // Also create an Expense record for finance tracking
        Expense financeExpense = Expense.builder()
                .companyId(dto.getCompanyId())
                .amount(dto.getAmount())
                .description("Servis Gideri #" + dto.getServiceTicketId() + ": " + dto.getDescription())
                .date(LocalDate.now())
                .category(ExpenseCategory.MATERIAL)
                .build();
        expenseRepository.save(financeExpense);

        // Audit log
        auditLogService.log("CREATE", "SERVICE_EXPENSE", saved.getId(),
                "Servis gideri eklendi: " + saved.getDescription() + " (" + saved.getAmount() + " ₺)");

        return mapToDTO(saved);
    }

    /**
     * Delete an expense
     */
    @Transactional
    public void deleteExpense(Long id) {
        ServiceTicketExpense expense = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gider bulunamadı: " + id));

        auditLogService.log("DELETE", "SERVICE_EXPENSE", id,
                "Servis gideri silindi: " + expense.getDescription());

        repository.deleteById(id);
    }

    private ServiceTicketExpenseDTO mapToDTO(ServiceTicketExpense expense) {
        return ServiceTicketExpenseDTO.builder()
                .id(expense.getId())
                .serviceTicketId(expense.getServiceTicketId())
                .companyId(expense.getCompanyId())
                .description(expense.getDescription())
                .amount(expense.getAmount())
                .supplier(expense.getSupplier())
                .notes(expense.getNotes())
                .createdAt(expense.getCreatedAt())
                .build();
    }
}
