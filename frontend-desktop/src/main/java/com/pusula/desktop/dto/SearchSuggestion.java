package com.pusula.desktop.dto;

public class SearchSuggestion {
    public enum Type {
        CUSTOMER, TECHNICIAN
    }

    private final Long id;
    private final String name;
    private final String phone;
    private final Type type;

    public SearchSuggestion(Long id, String name, String phone, Type type) {
        this.id = id;
        this.name = name;
        this.phone = phone != null ? phone : "";
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPhone() {
        return phone;
    }

    public Type getType() {
        return type;
    }

    @Override
    public String toString() {
        String typeLabel = (type == Type.CUSTOMER) ? "Müşteri" : "Teknisyen";
        if (phone != null && !phone.isEmpty()) {
            return name + " - " + phone + " (" + typeLabel + ")";
        }
        return name + " (" + typeLabel + ")";
    }
}
