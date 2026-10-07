package com.pusula.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "signature_erasure_tasks")
public class SignatureErasureTask {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false, length = 500) private String filePath;
    @Column(nullable = false) private LocalDateTime createdAt;
    protected SignatureErasureTask() {}
    public SignatureErasureTask(Long userId, String filePath) {
        this.userId = userId; this.filePath = filePath; createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
    }
    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public String getFilePath() { return filePath; }
}
