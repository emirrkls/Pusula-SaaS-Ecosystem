package com.pusula.desktop.dto;

public record UpdateCustomerWhatsAppConsentRequest(
        Boolean optedIn,
        WhatsAppConsentSource source) {
}
