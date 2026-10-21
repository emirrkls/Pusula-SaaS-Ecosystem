package com.pusula.backend.support;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component @ConfigurationProperties(prefix="app.support") @Getter @Setter
public class SupportProperties {
    // Deliberate release gate. Domain/mailbox verification and pilot are required before enabling.
    private boolean enabled=false;
    private String email="destek@pusulaiklimlendirme.store";
    private String website="https://pusulaiklimlendirme.store/destek";
    private String notifyEmail="";
    private String mailFrom="";
}
