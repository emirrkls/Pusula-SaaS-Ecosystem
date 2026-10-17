package com.pusula.backend.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="support_attachments") @Getter @Setter
public class SupportAttachment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long messageId;
    private String fileName;
    private String mediaType;
    @Column(columnDefinition="bytea") private byte[] content;
    private Instant createdAt;
}
