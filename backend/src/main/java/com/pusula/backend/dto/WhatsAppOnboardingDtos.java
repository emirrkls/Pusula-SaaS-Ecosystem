package com.pusula.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public final class WhatsAppOnboardingDtos {
    private WhatsAppOnboardingDtos() {}

    public record StartResponse(String url, String appId, String configurationId, LocalDateTime expiresAt) {}
    public record StatusResponse(boolean connected, String displayPhoneNumber, String verifiedName,
                                 String status, LocalDateTime tokenExpiresAt, LocalDateTime updatedAt,
                                 SyncStatus sync) {
        public StatusResponse(boolean connected, String phone, String name, String status,
                              LocalDateTime expiry, LocalDateTime updated) {
            this(connected, phone, name, status, expiry, updated, null);
        }
    }
    public record SyncStatus(String status, String contactsStatus, String historyStatus, int historyProgress) {}
    public record CompleteRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{40,80}$") String state,
            @NotBlank @Size(max = 4096) String code,
            @NotBlank @Pattern(regexp = "^[0-9]{5,32}$") String wabaId,
            @Pattern(regexp = "^[0-9]{5,32}$") String phoneNumberId,
            Boolean coexistence) {
        public CompleteRequest(String state, String code, String wabaId, String phoneNumberId) {
            this(state, code, wabaId, phoneNumberId, null);
        }
    }
    public record ProgressRequest(@NotBlank @Pattern(regexp = "^[A-Za-z0-9_-]{40,80}$") String state) {}
    public record CompleteResponse(boolean connected, String displayPhoneNumber, String verifiedName, SyncStatus sync) {
        public CompleteResponse(boolean connected, String phone, String name) { this(connected, phone, name, null); }
    }
}
