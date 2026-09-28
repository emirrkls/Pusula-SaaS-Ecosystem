package com.pusula.backend.dto;

import com.pusula.backend.entity.WhatsAppConsentSource;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerWhatsAppConsentRequest(
        @NotNull(message = "WhatsApp izin durumu zorunludur.") Boolean optedIn,
        WhatsAppConsentSource source) {
}
