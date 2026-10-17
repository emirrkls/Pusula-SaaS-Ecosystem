package com.pusula.backend.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="support_messages",uniqueConstraints=@UniqueConstraint(columnNames={"author_id","client_key"}))
@Getter @Setter
public class SupportMessage {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long ticketId;
    private Long authorId;
    private boolean staff;
    private boolean internalNote;
    @Column(length=4000) private String body;
    private String clientKey;
    private String fingerprint;
    private Instant createdAt;
}
