package com.pusula.backend.dto;

import com.pusula.backend.entity.ServiceTicket.TicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ServiceTicketDTO {
    private UUID id;
    private UUID customerId;
    private UUID assignedTechnicianId;
    private TicketStatus status;
    private LocalDateTime scheduledDate;
    private String description;
    private String notes;
    private LocalDateTime createdAt;
}
