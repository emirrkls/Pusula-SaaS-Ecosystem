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
                        if (value != null && !value.isBlank()) return value;
                    }
                }
            }
        } catch (Exception ignored) {
            // Deliberately return a stable user-facing fallback.
        }
        return fallback;
    }
}
