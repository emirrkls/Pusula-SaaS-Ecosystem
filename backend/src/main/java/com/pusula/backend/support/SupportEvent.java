package com.pusula.backend.support;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity @Table(name="support_events") @Getter @Setter
public class SupportEvent {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    private Long ticketId;
    private Long actorId;
    private String eventType;
    @Column(name="event_value") private String value;
    private Instant createdAt;
}
