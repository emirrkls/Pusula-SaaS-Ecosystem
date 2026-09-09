package com.pusula.desktop.dto;

public class AccountPartyOptionDTO {
    private Long id;
    private String displayName;
    private String partyType;

    public AccountPartyOptionDTO() {}
    public AccountPartyOptionDTO(Long id, String displayName, String partyType) {
        this.id = id;
        this.displayName = displayName;
        this.partyType = partyType;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getPartyType() { return partyType; }
    @Override public String toString() { return displayName != null ? displayName : "Kurum"; }
}
