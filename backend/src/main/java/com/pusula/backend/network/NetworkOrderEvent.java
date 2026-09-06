package com.pusula.backend.network;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "service_network_order_events") @Getter @Setter
public class NetworkOrderEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long orderId;
    @Column(nullable = false) private Long actorCompanyId;
    private Long actorUserId;
    @Column(nullable = false, length = 40) private String action;
    @Column(length = 1000) private String note;
    @Column(nullable = false) private LocalDateTime createdAt;
}
