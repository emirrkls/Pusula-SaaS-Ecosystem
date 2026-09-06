package com.pusula.backend.network;

import java.time.LocalDateTime;

/** Operational snapshot only; no financial values, customer records or private notes. */
public record NetworkTicketChanged(Long ticketId, Long companyId, Long actorUserId,
        String status, LocalDateTime scheduledDate, LocalDateTime scheduledEndDate) {}
