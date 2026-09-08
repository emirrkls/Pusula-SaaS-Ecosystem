package com.pusula.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class WhatsAppOnboardingDtos {
    private WhatsAppOnboardingDtos() {}

    public record StartResponse(String url, String appId, String configurationId, LocalDateTime expiresAt) {}
    public record StatusResponse(boolean connected, String displayPhoneNumber, String verifiedName,
                                 String status, LocalDateTime tokenExpiresAt, LocalDateTime updatedAt) {}
    public record CompleteRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{40,80}$") String state,
            @NotBlank @Size(max = 4096) String code,
            @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String wabaId,
            @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String phoneNumberId) {}
    public record CompleteResponse(boolean connected, String displayPhoneNumber, String verifiedName) {}
}
