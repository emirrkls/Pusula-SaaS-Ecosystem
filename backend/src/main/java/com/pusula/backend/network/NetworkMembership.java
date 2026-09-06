package com.pusula.backend.network;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity @Table(name = "service_network_memberships") @Getter @Setter
public class NetworkMembership {
    public enum Status { INVITED, ACTIVE, DECLINED, CLOSED }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long parentCompanyId;
    @Column(nullable = false) private Long childCompanyId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private String parentName;
    @Column(nullable = false) private String childName;
    private String region;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @Version private long version;
}
