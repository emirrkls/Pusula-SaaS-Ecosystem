package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Column(name = "fixed_expense_id")
    private Long fixedExpenseId;

    /** Payment method for income-like records such as commercial device sales. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method")
    private PaymentMethod paymentMethod;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "source_type", length = 40)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "financial_treatment", nullable = false)
    private ExpenseTreatment financialTreatment = ExpenseTreatment.OPERATING_EXPENSE;

    @PrePersist
    void applyFinancialTreatmentDefault() {
        if (financialTreatment == null) {
            financialTreatment = ExpenseTreatment.OPERATING_EXPENSE;
        }
    }
}
