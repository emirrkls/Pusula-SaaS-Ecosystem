package com.pusula.backend.dto;

public class RegisterRequest {
    private Long companyId;
    private String username;
    private String password;
    private String fullName;
    private String role; // SUPER_ADMIN, COMPANY_ADMIN, TECHNICIAN

    public RegisterRequest() {
    }

    public RegisterRequest(Long companyId, String username, String password, String fullName, String role) {
        this.companyId = companyId;
        this.username = username;
        this.password = password;
        this.fullName = fullName;
        this.role = role;
    }

    public static RegisterRequestBuilder builder() {
        return new RegisterRequestBuilder();
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public static class RegisterRequestBuilder {
        private Long companyId;
        private String username;
        private String password;
        private String fullName;
        private String role;

        RegisterRequestBuilder() {
        }

        public RegisterRequestBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public RegisterRequestBuilder username(String username) {
            this.username = username;
            return this;
        }

        public RegisterRequestBuilder password(String password) {
            this.password = password;
            return this;
        }

        public RegisterRequestBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public RegisterRequestBuilder role(String role) {
            this.role = role;
            return this;
        }

        public RegisterRequest build() {
            return new RegisterRequest(companyId, username, password, fullName, role);
        }
    }
}
