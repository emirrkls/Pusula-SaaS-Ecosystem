package com.pusula.desktop.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class CurrentAccountHistoryDTO {
    private Long accountId;
    private Long customerId;
    private String customerName;
    private BigDecimal currentBalance;
    private List<Transaction> transactions;

    public Long getAccountId() { return accountId; }
    public Long getCustomerId() { return customerId; }
    public String getCustomerName() { return customerName; }
    public BigDecimal getCurrentBalance() { return currentBalance; }
    public List<Transaction> getTransactions() { return transactions; }

    public static class Transaction {
        private Long id;
        private String type;
        private BigDecimal amount;
        private BigDecimal balanceAfter;
        private LocalDate effectiveDate;
        private String description;
        private String paymentMethod;
        private String sourceType;
        private Long sourceId;
        private LocalDateTime createdAt;

        public Long getId() { return id; }
        public String getType() { return type; }
        public BigDecimal getAmount() { return amount; }
        public BigDecimal getBalanceAfter() { return balanceAfter; }
        public LocalDate getEffectiveDate() { return effectiveDate; }
        public String getDescription() { return description; }
        public String getPaymentMethod() { return paymentMethod; }
        public String getSourceType() { return sourceType; }
        public Long getSourceId() { return sourceId; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
