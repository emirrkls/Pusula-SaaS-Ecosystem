package com.pusula.desktop.util;

import com.pusula.desktop.dto.CustomerDTO;

import java.util.Locale;

public final class CustomerSearchSupport {

    private static final Locale TURKISH = Locale.forLanguageTag("tr-TR");

    private CustomerSearchSupport() {
    }

    public static String displayText(CustomerDTO customer) {
        if (customer == null) {
            return "";
        }

        String name = customer.getName() == null ? "" : customer.getName().trim();
        String phone = customer.getPhone() == null || customer.getPhone().isBlank()
                ? "-"
                : customer.getPhone().trim();
        return name + " - " + phone;
    }

    public static boolean matches(CustomerDTO customer, String query) {
        if (customer == null || query == null || query.isBlank()) {
            return customer != null;
        }

        String normalizedQuery = normalizeText(query);
        String searchableText = normalizeText(safe(customer.getName()) + " " + safe(customer.getPhone()));
        if (searchableText.contains(normalizedQuery)) {
            return true;
        }

        String phoneQuery = normalizePhone(query);
        return !phoneQuery.isEmpty() && normalizePhone(customer.getPhone()).contains(phoneQuery);
    }

    private static String normalizeText(String value) {
        return safe(value)
                .toLowerCase(TURKISH)
                .replace('ç', 'c')
                .replace('ğ', 'g')
                .replace('ı', 'i')
                .replace('ö', 'o')
                .replace('ş', 's')
                .replace('ü', 'u')
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String digitsOnly(String value) {
        return safe(value).replaceAll("\\D", "");
    }

    private static String normalizePhone(String value) {
        String digits = digitsOnly(value);
        if (digits.startsWith("0090") && digits.length() > 4) {
            return "0" + digits.substring(4);
        }
        if (digits.startsWith("90") && digits.length() > 2) {
            return "0" + digits.substring(2);
        }
        return digits;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
