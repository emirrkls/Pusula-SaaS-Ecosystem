package com.pusula.backend.dto;

public class AuthResponse {
    private String token;
    private String role;

    public AuthResponse() {
    }

    public AuthResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }

    public static AuthResponseBuilder builder() {
        return new AuthResponseBuilder();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public static class AuthResponseBuilder {
        private String token;
        private String role;

        AuthResponseBuilder() {
        }

        public AuthResponseBuilder token(String token) {
            this.token = token;
            return this;
        }

        public AuthResponseBuilder role(String role) {
            this.role = role;
            return this;
        }

        public AuthResponse build() {
            return new AuthResponse(token, role);
        }
    }
}
