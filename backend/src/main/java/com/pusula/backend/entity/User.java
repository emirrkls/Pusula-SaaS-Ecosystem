package com.pusula.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

@Entity
@Table(name = "users")
@SQLDelete(sql = "UPDATE users SET is_deleted = true WHERE id = ?")
@Where(clause = "is_deleted = false")
public class User extends BaseEntity implements UserDetails {

    @Column(nullable = false)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role; // SUPER_ADMIN, COMPANY_ADMIN, TECHNICIAN

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "signature_path")
    private String signaturePath;

    public User() {
    }

    public User(Long id, Long companyId, String username, String passwordHash, String role, String fullName,
            String signaturePath, LocalDateTime createdAt) {
        this.setId(id);
        this.setCompanyId(companyId);
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.fullName = fullName;
        this.signaturePath = signaturePath;
        this.setCreatedAt(createdAt);
    }

    public static UserBuilder builder() {
        return new UserBuilder();
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSignaturePath() {
        return signaturePath;
    }

    public void setSignaturePath(String signaturePath) {
        this.signaturePath = signaturePath;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> authorities = new ArrayList<>();
        String normalizedRole = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        // Spring Security's hasAnyRole() expects authorities with ROLE_ prefix
        authorities.add(new SimpleGrantedAuthority("ROLE_" + normalizedRole));

        switch (normalizedRole) {
            case "SUPER_ADMIN":
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_READ_COMPANIES"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_WRITE_COMPANY"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_SUSPEND_COMPANY"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_RESET_PASSWORD"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_VIEW_SYSTEM"));
                break;
            case "SUPER_ADMIN_OPS":
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_READ_COMPANIES"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_SUSPEND_COMPANY"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_VIEW_SYSTEM"));
                break;
            case "SUPER_ADMIN_READONLY":
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_READ_COMPANIES"));
                authorities.add(new SimpleGrantedAuthority("SUPERADMIN_VIEW_SYSTEM"));
                break;
            default:
                break;
        }
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    public static class UserBuilder {
        private Long id;
        private Long companyId;
        private String username;
        private String passwordHash;
        private String role;
        private String fullName;
        private String signaturePath;
        private LocalDateTime createdAt;

        UserBuilder() {
        }

        public UserBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public UserBuilder companyId(Long companyId) {
            this.companyId = companyId;
            return this;
        }

        public UserBuilder username(String username) {
            this.username = username;
            return this;
        }

        public UserBuilder passwordHash(String passwordHash) {
            this.passwordHash = passwordHash;
            return this;
        }

        public UserBuilder role(String role) {
            this.role = role;
            return this;
        }

        public UserBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public UserBuilder signaturePath(String signaturePath) {
            this.signaturePath = signaturePath;
            return this;
        }

        public UserBuilder createdAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public User build() {
            return new User(id, companyId, username, passwordHash, role, fullName, signaturePath, createdAt);
        }
    }
}
