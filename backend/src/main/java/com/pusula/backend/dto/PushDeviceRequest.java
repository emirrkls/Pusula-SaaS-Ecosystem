package com.pusula.backend.dto;

import com.pusula.backend.entity.PushEnvironment;
import com.pusula.backend.entity.PushPlatform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushDeviceRequest(
        @NotBlank(message = "Device token is required")
        @Size(max = 200, message = "Device token is too long")
        @Pattern(regexp = "^[0-9a-fA-F]+$", message = "Device token must be hexadecimal")
        String token,
        @NotNull(message = "Platform is required") PushPlatform platform,
        @NotNull(message = "Environment is required") PushEnvironment environment,
        @NotBlank(message = "Bundle ID is required")
        @Size(max = 255, message = "Bundle ID is too long") String bundleId) {
    @Override
    public String toString() {
        return "PushDeviceRequest[platform=" + platform + ", environment=" + environment
                + ", bundleId=" + bundleId + ", token=<redacted>]";
    }
}
