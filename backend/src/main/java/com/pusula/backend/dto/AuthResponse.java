package com.pusula.backend.dto;

import java.util.Map;

public class AuthResponse {
    private String token;
    private String role;
    private String fullName;
    private Long companyId;
    private String companyName;
    // SaaS fields
    private String planType;
    private Map<String, Boolean> features;
    private QuotaDTO quota;
    private boolean isReadOnly;
    private Integer trialDaysRemaining;

    public AuthResponse() {
    }

    // --- Getters / Setters ---

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getPlanType() { return planType; }
    public void setPlanType(String planType) { this.planType = planType; }

    public Map<String, Boolean> getFeatures() { return features; }
    public void setFeatures(Map<String, Boolean> features) { this.features = features; }

    public QuotaDTO getQuota() { return quota; }
    public void setQuota(QuotaDTO quota) { this.quota = quota; }

    public boolean isReadOnly() { return isReadOnly; }
    public void setReadOnly(boolean readOnly) { isReadOnly = readOnly; }

    public Integer getTrialDaysRemaining() { return trialDaysRemaining; }
    public void setTrialDaysRemaining(Integer trialDaysRemaining) { this.trialDaysRemaining = trialDaysRemaining; }

    // --- Builder ---

    public static AuthResponseBuilder builder() { return new AuthResponseBuilder(); }

    public static class AuthResponseBuilder {
        private final AuthResponse instance = new AuthResponse();

        public AuthResponseBuilder token(String token) { instance.token = token; return this; }
        public AuthResponseBuilder role(String role) { instance.role = role; return this; }
        public AuthResponseBuilder fullName(String fullName) { instance.fullName = fullName; return this; }
        public AuthResponseBuilder companyId(Long companyId) { instance.companyId = companyId; return this; }
        public AuthResponseBuilder companyName(String companyName) { instance.companyName = companyName; return this; }
        public AuthResponseBuilder planType(String planType) { instance.planType = planType; return this; }
        public AuthResponseBuilder features(Map<String, Boolean> features) { instance.features = features; return this; }
        public AuthResponseBuilder quota(QuotaDTO quota) { instance.quota = quota; return this; }
        public AuthResponseBuilder readOnly(boolean readOnly) { instance.isReadOnly = readOnly; return this; }
        public AuthResponseBuilder trialDaysRemaining(Integer trialDaysRemaining) { instance.trialDaysRemaining = trialDaysRemaining; return this; }

        public AuthResponse build() { return instance; }
    }
}
