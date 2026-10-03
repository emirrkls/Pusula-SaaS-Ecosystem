package com.pusula.backend.dto;

public class GoogleAuthRequest {
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 8192)
    private String idToken;
    @jakarta.validation.constraints.Size(max = 255)
    private String preferredUsername;

    public GoogleAuthRequest() {
    }

    public String getIdToken() {
        return idToken;
    }

    public void setIdToken(String idToken) {
        this.idToken = idToken;
    }

    public String getPreferredUsername() {
        return preferredUsername;
    }

    public void setPreferredUsername(String preferredUsername) {
        this.preferredUsername = preferredUsername;
    }
}
