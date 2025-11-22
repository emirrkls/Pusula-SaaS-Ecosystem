package com.pusula.backend.dto;

import java.util.UUID;

public class UserDTO {
    private UUID id;
    private String username;
    private String fullName;
    private String role;

    public UserDTO() {
    }

    public UserDTO(UUID id, String username, String fullName, String role) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
    }

    public static UserDTOBuilder builder() {
        return new UserDTOBuilder();
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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

    public static class UserDTOBuilder {
        private UUID id;
        private String username;
        private String fullName;
        private String role;

        UserDTOBuilder() {
        }

        public UserDTOBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public UserDTOBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserDTOBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserDTOBuilder role(String role) {
            this.role = role;
            return this;
        }

        public UserDTO build() {
            return new UserDTO(id, username, fullName, role);
        }
    }
}
