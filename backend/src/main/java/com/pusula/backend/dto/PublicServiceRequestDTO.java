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
public class PublicServiceRequestDTO {
    private UUID companyId;
    private String customerName;
    private String customerPhone;
    private String customerAddress;
    private String description;
}
