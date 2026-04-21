package com.pusula.backend.dto;

public class AuthRequest {
    private String username;
    private String password;
    private String orgCode; // NULL = individual login, non-null = corporate login

    public AuthRequest() {
    }

    public AuthRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public AuthRequest(String username, String password, String orgCode) {
        this.username = username;
        this.password = password;
        this.orgCode = orgCode;
    }

    public static AuthRequestBuilder builder() {
        return new AuthRequestBuilder();
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

    public String getOrgCode() {
        return orgCode;
    }

    public void setOrgCode(String orgCode) {
        this.orgCode = orgCode;
    }

    public static class AuthRequestBuilder {
        private String username;
        private String password;
        private String orgCode;

        AuthRequestBuilder() {
        }

        public AuthRequestBuilder username(String username) {
            this.username = username;
            return this;
        }

        public AuthRequestBuilder password(String password) {
            this.password = password;
            return this;
        }

        public AuthRequestBuilder orgCode(String orgCode) {
            this.orgCode = orgCode;
            return this;
        }

        public AuthRequest build() {
            return new AuthRequest(username, password, orgCode);
        }
    }
}
