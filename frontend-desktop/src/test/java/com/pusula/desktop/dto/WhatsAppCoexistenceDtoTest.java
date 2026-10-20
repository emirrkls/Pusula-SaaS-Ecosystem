package com.pusula.desktop.dto;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhatsAppCoexistenceDtoTest {
    private final Gson gson = new com.google.gson.GsonBuilder()
        .registerTypeAdapter(java.time.LocalDateTime.class, new com.pusula.desktop.util.LocalDateTimeTypeAdapter())
        .create();
    @Test void oldServerResponseRemainsCompatible() {
        var status = gson.fromJson("{\"connected\":true,\"status\":\"CONNECTED\"}", WhatsAppIntegrationStatusDTO.class);
        assertTrue(status.isConnected()); assertNull(status.getSync());
    }
    @Test void acceptedHistoryIsNotPresentedAsReadyAndDeclinedIsNotAnError() {
        var status = gson.fromJson("{\"sync\":{\"status\":\"SYNCING\",\"historyStatus\":\"ACCEPTED\",\"historyProgress\":25}}",
            WhatsAppIntegrationStatusDTO.class);
        assertTrue(status.getSync().description().contains("%25"));
        status = gson.fromJson("{\"sync\":{\"status\":\"READY\",\"historyStatus\":\"DECLINED\"}}", WhatsAppIntegrationStatusDTO.class);
        assertTrue(status.getSync().description().contains("paylaşmama"));
    }
    @Test void messagesArePlainTextAndUnsupportedMediaHasAnHonestPlaceholder() {
        var message = gson.fromJson("{\"payload\":{\"type\":\"image\",\"from\":\"905550000000\"}}", WhatsAppMirrorItemDTO.class);
        assertTrue(message.messageLabel().contains("[image]"));
        message = gson.fromJson("{\"payload\":{\"type\":\"text\",\"text\":\"<script>not executed</script>\"}}", WhatsAppMirrorItemDTO.class);
        assertTrue(message.messageLabel().contains("<script>not executed</script>"));
    }
}
