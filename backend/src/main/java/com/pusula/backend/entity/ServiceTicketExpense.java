package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * ServiceTicketExpense - External costs for a service ticket
 * (e.g., parts bought from outside, outsourced services)
 * These are expenses that don't count as income for the company
 */
@Entity
@Table(name = "service_ticket_expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceTicketExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long serviceTicketId;

    @Column(nullable = false)
    private Long companyId;

    /**
     * Description of the external expense (e.g., "Dış parça: Kompresör")
     */
    @Column(nullable = false)
    private String description;

    /**
     * Cost of the external expense
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    /**
     * Supplier/vendor name (optional)
     */
    private String supplier;

    /**
     * Notes about the expense
     */
    @Column(length = 500)
    private String notes;

    /** Business date on which this expense belongs in finance reports. */
    @Column(name = "expense_date", nullable = false)
    private LocalDate expenseDate;

    /** Paired row in the finance ledger, used for consistent deletion. */
    @Column(name = "finance_expense_id")
    private Long financeExpenseId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
