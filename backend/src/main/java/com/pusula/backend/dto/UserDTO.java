package com.pusula.backend.dto;

public class UserDTO {
    private Long id;
    private String username;
    private String fullName;
    private String role;
    private String password; // Optional - only used for creation or reset

    public UserDTO() {
    }

    public UserDTO(Long id, String username, String fullName, String role, String password) {
        this.id = id;
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.password = password;
    }

    public static UserDTOBuilder builder() {
        return new UserDTOBuilder();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
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

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public static class UserDTOBuilder {
        private Long id;
        private String username;
        private String fullName;
        private String role;
        private String password;

        UserDTOBuilder() {
        }

        public UserDTOBuilder id(Long id) {
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

        public UserDTOBuilder password(String password) {
            this.password = password;
            return this;
        }

        public UserDTO build() {
            return new UserDTO(id, username, fullName, role, password);
        }
    }
}
