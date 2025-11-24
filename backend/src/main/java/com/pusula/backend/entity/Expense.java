package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "expenses")
@SQLDelete(sql = "UPDATE expenses SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class Expense extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExpenseType type;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate date;

    private String description;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    public Expense() {
    }

    public Expense(Long id, Long companyId, ExpenseType type, BigDecimal amount, LocalDate date, String description,
            Long userId) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.type = type;
        this.amount = amount;
        this.date = date;
        this.description = description;
        this.userId = userId;
    }

    public static ExpenseBuilder builder() {
        return new ExpenseBuilder();
    }

    public ExpenseType getType() {
        return type;
    }

    public void setType(ExpenseType type) {
        this.type = type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public enum ExpenseType {
        FOOD, FUEL, RENT, OTHER
    }

    public static class ExpenseBuilder {
        private Long id;
        private Long companyId;
        private ExpenseType type;
        private BigDecimal amount;
        private LocalDate date;
        private String description;
        private Long userId;

        ExpenseBuilder() {
        }

        public ExpenseBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public ExpenseBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public ExpenseBuilder type(ExpenseType type) {
            this.type = type;
            return this;
        }

        public ExpenseBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public ExpenseBuilder date(LocalDate date) {
            this.date = date;
            return this;
        }

        public ExpenseBuilder description(String description) {
            this.description = description;
            return this;
        }

        public ExpenseBuilder userId(Long userId) {
            this.userId = userId;
            return this;
        }

        public Expense build() {
            return new Expense(id, companyId, type, amount, date, description, userId);
        }
    }
}
