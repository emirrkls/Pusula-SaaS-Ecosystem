package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * CompanyDebt - Tracks debts the company owes to suppliers/vendors
 * (Borçlarımız - Our Debts)
 */
@Entity
@Table(name = "company_debts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Where(clause = "deleted = false")
public class CompanyDebt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long companyId;

    /**
     * Who we owe money to (e.g., supplier name)
     */
    @Column(nullable = false)
    private String creditorName;

    /**
     * Description of what the debt is for
     */
    @Column(length = 500)
    private String description;

    /**
     * Original debt amount
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal originalAmount;

    /**
     * Remaining unpaid amount
     */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal remainingAmount;

    /**
     * Date debt was incurred
     */
    @Column(nullable = false)
    private LocalDate debtDate;

    /**
     * Due date for payment (optional)
     */
    private LocalDate dueDate;

    /**
     * Contact info for the creditor
     */
    private String creditorPhone;

    /**
     * Status of the debt
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DebtStatus status = DebtStatus.UNPAID;

    /**
     * Notes about the debt
     */
    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private boolean deleted = false;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (remainingAmount == null) {
            remainingAmount = originalAmount;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        // Auto-set status based on remaining amount
        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            status = DebtStatus.PAID;
        } else if (remainingAmount.compareTo(originalAmount) < 0) {
            status = DebtStatus.PARTIAL;
        }
    }

    public enum DebtStatus {
        UNPAID, // Ödenmedi
        PARTIAL, // Kısmi Ödeme
        PAID // Ödendi
    }
}
