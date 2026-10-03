package com.pusula.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "apple_auth_challenges")
@Getter @Setter
public class AppleAuthChallenge {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 64) private String nonce;
    @Column(name = "expires_at", nullable = false) private LocalDateTime expiresAt;
    @Column(name = "used_at") private LocalDateTime usedAt;
}
