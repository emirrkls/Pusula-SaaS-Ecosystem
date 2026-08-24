package com.pusula.desktop.util;

import com.google.gson.JsonParser;
import retrofit2.Response;

/** Extracts the backend's standard error envelope without leaking raw payloads. */
public final class ApiErrorHelper {
    private ApiErrorHelper() {}

    public static String message(Response<?> response, String fallback) {
        if (response == null || response.errorBody() == null) return fallback;
        try {
            String body = response.errorBody().string();
            var json = JsonParser.parseString(body);
            if (json.isJsonObject()) {
                var object = json.getAsJsonObject();
                for (String key : new String[]{"message", "error"}) {
                    if (object.has(key) && !object.get(key).isJsonNull()) {
                        String value = object.get(key).getAsString();
                        if (value != null && !value.isBlank()) return userFacing(value);
                    }
                }
            }
        } catch (Exception ignored) {
            // Deliberately return a stable user-facing fallback.
        }
        return fallback;
    }

    /** Prevents database/stack-trace details from leaking into customer-facing dialogs. */
    public static String userFacing(String message) {
        if (message == null || message.isBlank()) return "İşlem tamamlanamadı. Lütfen tekrar deneyin.";
        String value = message.trim();
        String normalized = value.toLowerCase(java.util.Locale.ROOT);

        if (normalized.contains("uq_inventory_company_barcode_normalized_active")) {
            return "Bu barkod işletmenizde aktif başka bir ürün tarafından kullanılıyor. "
                    + "Ürünün barkodunu kontrol edip tekrar deneyin.";
        }
        if (normalized.contains("duplicate key value")
                || normalized.contains("violates unique constraint")
                || normalized.contains("sqlstate")
                || normalized.contains("org.postgresql")) {
            return "Bu işlem mevcut bir kayıtla çakıştı. Ekranı yenileyip bilgileri kontrol ederek tekrar deneyin.";
        }
        if (normalized.contains("exception:") || normalized.contains("\n\tat ")) {
            return "Beklenmeyen bir teknik hata oluştu. Lütfen tekrar deneyin; sorun devam ederse destek ekibine bildirin.";
        }
        return value;
    }
}
