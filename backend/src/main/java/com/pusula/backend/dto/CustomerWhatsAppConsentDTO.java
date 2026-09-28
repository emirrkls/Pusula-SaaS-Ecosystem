package com.pusula.backend.dto;

import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.WhatsAppConsentSource;

import java.time.LocalDateTime;

public record CustomerWhatsAppConsentDTO(
        Long customerId,
        boolean optedIn,
        LocalDateTime optedInAt,
        WhatsAppConsentSource source,
        LocalDateTime optedOutAt) {

    public static CustomerWhatsAppConsentDTO from(Customer customer) {
        return new CustomerWhatsAppConsentDTO(
                customer.getId(),
                customer.isWhatsappOptIn(),
                customer.getWhatsappOptInAt(),
                customer.getWhatsappOptInSource(),
                customer.getWhatsappOptOutAt());
    }
}
