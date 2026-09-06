package com.pusula.backend.network;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** Per-company network entitlement. Independent from retail subscriptions. */
@Entity @Table(name = "service_network_policies") @Getter @Setter
public class NetworkPolicy {
    @Id private Long companyId;
    private boolean enabled;
    private int maxMembers;
    private int maxMonthlyOrders;
    @Version private long version;
}
