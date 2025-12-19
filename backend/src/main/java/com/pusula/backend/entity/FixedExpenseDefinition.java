package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "fixed_expense_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FixedExpenseDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false)
    private String name;

    @Column(name = "default_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal defaultAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseCategory category;

    @Column(name = "day_of_month")
    private Integer dayOfMonth; // Day of month for payment (1-31)

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    @Builder.Default
    private ExpenseFrequency frequency = ExpenseFrequency.MONTHLY;

    @Column(name = "is_paid")
    @Builder.Default
    private Boolean isPaid = false;
}
