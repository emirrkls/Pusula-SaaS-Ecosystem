package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "daily_closings", uniqueConstraints = @UniqueConstraint(columnNames = { "date", "company_id" }))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyClosing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "total_income", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalIncome;

    @Column(name = "total_expense", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalExpense;

    @Column(name = "net_cash", nullable = false, precision = 10, scale = 2)
    private BigDecimal netCash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClosingStatus status;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "closed_by_user_id")
    private Long closedByUserId;

    public enum ClosingStatus {
        OPEN,
        CLOSED
    }
}
