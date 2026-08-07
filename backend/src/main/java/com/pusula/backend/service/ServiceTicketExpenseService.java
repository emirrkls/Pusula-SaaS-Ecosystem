package com.pusula.backend.service;

import com.pusula.backend.dto.ServiceTicketExpenseDTO;
import com.pusula.backend.entity.Expense;
import com.pusula.backend.entity.ExpenseCategory;
import com.pusula.backend.entity.ExpenseTreatment;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.ServiceTicketExpense;
import com.pusula.backend.repository.ExpenseRepository;
import com.pusula.backend.repository.ServiceTicketExpenseRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ServiceTicketExpenseService {

    private final ServiceTicketExpenseRepository repository;
    private final ExpenseRepository expenseRepository;
    private final ServiceTicketRepository ticketRepository;
    private final AuditLogService auditLogService;
    private final FinanceService financeService;
    private final ZoneId businessZone;

    public ServiceTicketExpenseService(ServiceTicketExpenseRepository repository,
            ExpenseRepository expenseRepository,
            ServiceTicketRepository ticketRepository,
            AuditLogService auditLogService,
            FinanceService financeService,
            @Value("${app.business.timezone:Europe/Istanbul}") String businessTimezone) {
        this.repository = repository;
        this.expenseRepository = expenseRepository;
        this.ticketRepository = ticketRepository;
        this.auditLogService = auditLogService;
        this.financeService = financeService;
        this.businessZone = ZoneId.of(businessTimezone);
    }

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
        if (dto.getDescription() == null || dto.getDescription().isBlank()) {
            throw new IllegalArgumentException("Gider açıklaması zorunludur.");
        }
        if (dto.getAmount() == null || dto.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Gider tutarı sıfırdan büyük olmalıdır.");
        }
        ServiceTicket ticket = ticketRepository.findById(dto.getServiceTicketId())
                .orElseThrow(() -> new RuntimeException("Servis fişi bulunamadı: " + dto.getServiceTicketId()));
        LocalDate expenseDate = resolveExpenseDate(ticket);

        // Create the finance row first so the service expense retains an exact link.
        Expense financeExpense = Expense.builder()
                .companyId(ticket.getCompanyId())
                .amount(dto.getAmount())
                .description("Servis Gideri #" + ticket.getId() + ": " + dto.getDescription().trim())
                .date(expenseDate)
                .category(ExpenseCategory.MATERIAL)
                .financialTreatment(ExpenseTreatment.SERVICE_DIRECT_EXPENSE)
                .build();
        Expense savedFinanceExpense = expenseRepository.save(financeExpense);

        ServiceTicketExpense expense = ServiceTicketExpense.builder()
                .serviceTicketId(ticket.getId())
                .companyId(ticket.getCompanyId())
                .description(dto.getDescription().trim())
                .amount(dto.getAmount())
                .supplier(dto.getSupplier())
                .notes(dto.getNotes())
                .expenseDate(expenseDate)
                .financeExpenseId(savedFinanceExpense.getId())
                .build();

        ServiceTicketExpense saved = repository.save(expense);
        financeService.reconcileClosedDay(ticket.getCompanyId(), expenseDate);

        // Audit log
        auditLogService.log("CREATE", "SERVICE_EXPENSE", saved.getId(),
                "Servis gideri eklendi: " + saved.getDescription() + " (" + saved.getAmount() + " ₺)");

        return mapToDTO(saved);
    }

    /**
     * Delete an expense
     */
    @Transactional
    public void deleteExpense(Long ticketId, Long id) {
        ServiceTicketExpense expense = repository.findByIdAndServiceTicketId(id, ticketId)
                .orElseThrow(() -> new RuntimeException("Gider bulunamadı: " + id));

        auditLogService.log("DELETE", "SERVICE_EXPENSE", id,
                "Servis gideri silindi: " + expense.getDescription());

        repository.deleteById(id);
        if (expense.getFinanceExpenseId() != null) {
            expenseRepository.deleteById(expense.getFinanceExpenseId());
        }
        financeService.reconcileClosedDay(expense.getCompanyId(), expense.getExpenseDate());
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
                .expenseDate(expense.getExpenseDate())
                .createdAt(expense.getCreatedAt())
                .build();
    }

    private LocalDate resolveExpenseDate(ServiceTicket ticket) {
        if (ticket.getCompletedAt() != null) {
            return ticket.getCompletedAt().toLocalDate();
        }
        if (ticket.getScheduledDate() != null) {
            return ticket.getScheduledDate().toLocalDate();
        }
        return LocalDate.now(businessZone);
    }
}
