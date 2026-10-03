package com.pusula.backend.dto;

import jakarta.validation.constraints.*;

public record AppleAuthRequest(
        @NotBlank @Size(max = 8192) String idToken,
        @NotBlank @Size(max = 2048) String authorizationCode,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9-]{36}") String challengeId,
        @Size(max = 200) String fullName) {}
