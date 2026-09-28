package com.pusula.desktop.dto;

import java.time.LocalDateTime;

public record CustomerWhatsAppConsentDTO(
        Long customerId,
        boolean optedIn,
        LocalDateTime optedInAt,
        WhatsAppConsentSource source,
        LocalDateTime optedOutAt) {
}
