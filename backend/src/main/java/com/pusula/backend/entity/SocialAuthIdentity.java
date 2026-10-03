package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "social_auth_identities")
@Getter @Setter
public class SocialAuthIdentity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 16) private String provider;
    @Column(nullable = false) private String subject;
    @Column(name = "user_id", nullable = false) private Long userId;
    @Column(name = "verified_email", length = 320) private String verifiedEmail;
    @Column(name = "refresh_token_ciphertext", columnDefinition = "TEXT") private String refreshTokenCiphertext;
    @Column(name = "created_at", nullable = false) private LocalDateTime createdAt = LocalDateTime.now();
}
