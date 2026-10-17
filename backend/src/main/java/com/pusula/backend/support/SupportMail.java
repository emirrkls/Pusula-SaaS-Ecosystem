package com.pusula.backend.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="support_mail_outbox") @Getter @Setter
public class SupportMail {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long ticketId;
    private Long messageId;
    private int attempts;
    private Instant nextAttemptAt;
    private Instant deliveredAt;
}
