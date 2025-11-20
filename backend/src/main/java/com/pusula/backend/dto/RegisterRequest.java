package com.pusula.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegisterRequest {
    private UUID companyId;
    private String username;
    private String password;
    private String fullName;
    private String role; // SUPER_ADMIN, COMPANY_ADMIN, TECHNICIAN
}
