package com.pusula.desktop.dto;

import java.math.BigDecimal;

/**
 * ExpenseDTO - Uses String for date field to avoid Gson/Jackson LocalDate
 * serialization issues in Java 21
 */
public class ExpenseDTO {
    private Long id;
    private Long companyId;
    private BigDecimal amount;
    private String description;
    private String date; // String format: "YYYY-MM-DD"
    private String category; // RENT, SALARY, BILLS, FUEL, FOOD, OTHER

    public ExpenseDTO() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
