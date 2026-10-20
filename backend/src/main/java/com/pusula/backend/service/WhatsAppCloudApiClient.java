package com.pusula.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Component
public class WhatsAppCloudApiClient {
    private final WhatsAppIntegrationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public WhatsAppCloudApiClient(WhatsAppIntegrationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper,
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    WhatsAppCloudApiClient(WhatsAppIntegrationProperties properties, ObjectMapper objectMapper,
                           HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public SendResult sendTemplate(String phoneNumberId, String accessToken, String recipient,
                                   String templateName, String language, List<String> parameters) {
        try {
            ObjectNode template = objectMapper.createObjectNode();
            template.put("name", templateName);
            template.putObject("language").put("code", language);
            ArrayNode bodyParameters = objectMapper.createArrayNode();
            for (String value : parameters) {
                bodyParameters.addObject().put("type", "text").put("text", value == null ? "" : value);
            }
            ObjectNode bodyComponent = objectMapper.createObjectNode();
            bodyComponent.put("type", "body");
            bodyComponent.set("parameters", bodyParameters);
            template.putArray("components").add(bodyComponent);

            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("messaging_product", "whatsapp");
            payload.put("recipient_type", "individual");
            payload.put("to", recipient.replace("+", ""));
            payload.put("type", "template");
            payload.set("template", template);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(graphUri(phoneNumberId + "/messages"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode responseBody = readBody(response.body());
            if (response.statusCode() / 100 != 2) {
                throw apiFailure("WhatsApp mesajı gönderilemedi", response.statusCode(), responseBody);
            }
            String messageId = responseBody.path("messages").path(0).path("id").asText();
            if (messageId.isBlank()) {
                throw new WhatsAppCloudApiException("Meta yanıtında mesaj kimliği bulunamadı", true);
            }
            return new SendResult(messageId);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WhatsAppCloudApiException("Meta mesaj çağrısı kesintiye uğradı", true, ex);
        } catch (WhatsAppCloudApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WhatsAppCloudApiException("Meta mesaj servisine ulaşılamadı", true, ex);
        }
    }

    public void subscribeApp(String wabaId, String accessToken) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(graphUri(wabaId + "/subscribed_apps"))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode body = readBody(response.body());
            if (response.statusCode() / 100 != 2 || !body.path("success").asBoolean(false)) {
                throw apiFailure("WhatsApp webhook aboneliği oluşturulamadı", response.statusCode(), body);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new WhatsAppCloudApiException("Meta abonelik çağrısı kesintiye uğradı", true, ex);
        } catch (WhatsAppCloudApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new WhatsAppCloudApiException("Meta abonelik servisine ulaşılamadı", true, ex);
        }
    }

    public String requestAppData(String phoneNumberId, String token, String syncType) {
        if (!List.of("smb_app_state_sync", "history").contains(syncType))
            throw new IllegalArgumentException("Unsupported sync type");
        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("messaging_product", "whatsapp"); body.put("sync_type", syncType);
            HttpRequest request = HttpRequest.newBuilder().uri(graphUri(phoneNumberId + "/smb_app_data"))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body))).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 429) throw new SyncRequestException("META_RATE_LIMIT", true);
            if (response.statusCode() >= 500) throw new SyncRequestException("REQUEST_OUTCOME_UNKNOWN", false);
            if (response.statusCode() / 100 != 2) throw new SyncRequestException("META_REJECTED", false);
            String requestId = readBody(response.body()).path("request_id").asText();
            if (requestId.isBlank() || requestId.length() > 160)
                throw new SyncRequestException("REQUEST_OUTCOME_UNKNOWN", false);
            return requestId;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SyncRequestException("REQUEST_OUTCOME_UNKNOWN", false);
        } catch (SyncRequestException ex) { throw ex; }
        catch (Exception ex) { throw new SyncRequestException("REQUEST_OUTCOME_UNKNOWN", false); }
    }

    public static class SyncRequestException extends RuntimeException {
        private final String code;
        private final boolean retryable;
        public SyncRequestException(String code, boolean retryable) {
            super(code); this.code = code; this.retryable = retryable;
        }
        public String code() { return code; }
        public boolean definitelyRejectedRetryable() { return retryable; }
    }

    private URI graphUri(String path) {
        return URI.create("https://graph.facebook.com/" + properties.getGraphVersion() + "/" + path);
    }

    private JsonNode readBody(String body) {
        try {
            return objectMapper.readTree(body == null || body.isBlank() ? "{}" : body);
        } catch (Exception ignored) {
            return objectMapper.createObjectNode();
        }
    }

    private WhatsAppCloudApiException apiFailure(String fallback, int status, JsonNode body) {
        String detail = body.path("error").path("message").asText();
        String message = detail.isBlank() ? fallback : fallback + ": " + detail;
        return new WhatsAppCloudApiException(truncate(message, 900), status == 429 || status >= 500);
    }

    private String truncate(String value, int limit) {
        return value.length() <= limit ? value : value.substring(0, limit);
    }

    public record SendResult(String messageId) {}

    public static class WhatsAppCloudApiException extends RuntimeException {
        private final boolean retryable;

        public WhatsAppCloudApiException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public WhatsAppCloudApiException(String message, boolean retryable, Throwable cause) {
            super(message, cause);
            this.retryable = retryable;
        }

        public boolean isRetryable() { return retryable; }
    }
}
