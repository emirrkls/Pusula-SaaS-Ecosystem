package com.pusula.desktop.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CurrentAccountDTO {
    private Long id;
    private Long companyId;
    private Long customerId;
    private String customerName;
    private Long partyId;
    private String partyType;
    private String accountName;
    private BigDecimal balance;
    private LocalDateTime lastUpdated;

    public CurrentAccountDTO() {
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

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getCustomerName() {
        return accountName != null && !accountName.isBlank() ? accountName : customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public Long getPartyId() { return partyId; }
    public void setPartyId(Long partyId) { this.partyId = partyId; }
    public String getPartyType() { return partyType; }
    public void setPartyType(String partyType) { this.partyType = partyType; }
    public String getAccountName() {
        return accountName != null && !accountName.isBlank() ? accountName : customerName;
    }
    public void setAccountName(String accountName) { this.accountName = accountName; }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public LocalDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(LocalDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}
