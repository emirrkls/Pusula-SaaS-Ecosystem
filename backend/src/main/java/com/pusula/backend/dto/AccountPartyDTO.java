package com.pusula.backend.dto;

import com.pusula.backend.entity.AccountParty;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPartyDTO {
    private Long id;
    private AccountParty.PartyType partyType;
    private String displayName;
    private String legalName;
    private String taxNumber;
    private String taxOffice;
    private String phone;
    private String email;
    private String address;
    private String contactPerson;
    private Integer paymentTermDays;
    private Long customerId;
    private boolean active;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
