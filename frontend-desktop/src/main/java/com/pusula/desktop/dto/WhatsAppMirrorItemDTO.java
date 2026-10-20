package com.pusula.desktop.dto;

import com.google.gson.JsonObject;
import java.time.LocalDateTime;

public class WhatsAppMirrorItemDTO {
    private String key;
    private String peerKey;
    private JsonObject payload;
    private LocalDateTime eventAt;
    public String getKey() { return key; }
    public String getPeerKey() { return peerKey; }
    public LocalDateTime getEventAt() { return eventAt; }
    private String text(String name) {
        return payload != null && payload.has(name) && payload.get(name).isJsonPrimitive()
            ? payload.get(name).getAsString() : "";
    }
    public String contactLabel() {
        return text("name").isBlank() ? text("phone") : text("name") + " — " + text("phone");
    }
    public String messageLabel() {
        String body = text("text");
        if (body.isBlank()) body = "[" + text("type") + "]";
        return (eventAt == null ? "" : eventAt.toString().replace('T', ' ') + "  ")
            + text("from") + (text("to").isBlank() ? "" : " → " + text("to")) + "\n" + body;
    }
}
