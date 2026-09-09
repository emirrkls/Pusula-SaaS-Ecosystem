package com.pusula.desktop.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountPartyDTO {
    private Long id;
    private String partyType;
    private String displayName;
    private String legalName;
    private String taxNumber;
    private String taxOffice;
    private String phone;
    private String email;
    private String address;
    private String contactPerson;
    private Integer paymentTermDays;
    private Boolean active;
    private String notes;
}
