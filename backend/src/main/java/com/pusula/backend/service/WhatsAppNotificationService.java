package com.pusula.backend.service;

import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * WhatsApp notification service for customer communications.
 * Supports both real API (when configured) and mock mode for development.
 * 
 * Integrations supported:
 * - WhatsApp Business API (Meta Cloud API)
 * - Netgsm WhatsApp API (Turkey-specific provider)
 * 
 * Feature-gated: requires WHATSAPP_INTEGRATION plan feature.
 */
@Service
public class WhatsAppNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WhatsAppNotificationService.class);

    private final CustomerRepository customerRepository;
    private final ServiceTicketRepository ticketRepository;

    @Value("${whatsapp.api.enabled:false}")
    private boolean apiEnabled;

    @Value("${whatsapp.api.token:}")
    private String apiToken;

    @Value("${whatsapp.api.phone-id:}")
    private String phoneNumberId;

    @Value("${whatsapp.api.provider:META}")
    private String provider; // META or NETGSM

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WhatsAppNotificationService(CustomerRepository customerRepository,
                                       ServiceTicketRepository ticketRepository) {
        this.customerRepository = customerRepository;
        this.ticketRepository = ticketRepository;
    }

    /**
     * Send service completion notification to customer.
     * Called after technician completes a ticket and processes payment.
     */
    @RequiresFeature("WHATSAPP_INTEGRATION")
    public void notifyServiceCompleted(Long ticketId, BigDecimal collectedAmount, BigDecimal remainingDebt) {
        ServiceTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || ticket.getCustomerId() == null) {
            log.warn("WhatsApp notification skipped: ticket {} not found or no customer", ticketId);
            return;
        }

        Customer customer = customerRepository.findById(ticket.getCustomerId()).orElse(null);
        if (customer == null || customer.getPhone() == null || customer.getPhone().isBlank()) {
            log.warn("WhatsApp notification skipped: customer has no phone number");
            return;
        }

        String message = buildCompletionMessage(customer.getName(), ticketId, collectedAmount, remainingDebt);
        sendMessage(customer.getPhone(), message);
    }

    /**
     * Send cari (current account) update notification.
     * Called when debt is added or reduced on a customer's account.
     */
    @RequiresFeature("WHATSAPP_INTEGRATION")
    public void notifyCariUpdate(Long customerId, BigDecimal newBalance) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || customer.getPhone() == null) return;

        String message = buildCariMessage(customer.getName(), newBalance);
        sendMessage(customer.getPhone(), message);
    }

    // ── Message Templates ──────────────────────────────────────

    private String buildCompletionMessage(String customerName, Long ticketId,
                                           BigDecimal collected, BigDecimal remainingDebt) {
        StringBuilder sb = new StringBuilder();
        sb.append("✅ *Servis Tamamlandı*\n\n");
        sb.append("Sayın ").append(customerName).append(",\n\n");
        sb.append("📋 Fiş No: #").append(ticketId).append("\n");
        sb.append("💰 Tahsil Edilen: ₺").append(String.format("%.2f", collected)).append("\n");

        if (remainingDebt != null && remainingDebt.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("⚠️ Kalan Borç (Cari): ₺").append(String.format("%.2f", remainingDebt)).append("\n");
        }

        sb.append("\nHizmetimizi tercih ettiğiniz için teşekkür ederiz. 🙏\n");
        sb.append("_Pusula Servis Yönetim Sistemi_");

        return sb.toString();
    }

    private String buildCariMessage(String customerName, BigDecimal balance) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Cari Hesap Bilgilendirmesi*\n\n");
        sb.append("Sayın ").append(customerName).append(",\n\n");

        if (balance.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("Güncel cari borcunuz: *₺").append(String.format("%.2f", balance)).append("*\n");
        } else {
            sb.append("Cari hesabınız temizlenmiştir. ✅\n");
        }

        sb.append("\n_Pusula Servis Yönetim Sistemi_");
        return sb.toString();
    }

    // ── Message Delivery ──────────────────────────────────────

    private void sendMessage(String phone, String message) {
        String normalizedPhone = normalizePhone(phone);

        if (!apiEnabled) {
            // Mock mode — log to console
            log.info("📱 [MOCK WhatsApp] To: {} | Message:\n{}", normalizedPhone, message);
            return;
        }

        try {
            if ("NETGSM".equalsIgnoreCase(provider)) {
                sendViaNetgsm(normalizedPhone, message);
            } else {
                sendViaMeta(normalizedPhone, message);
            }
        } catch (Exception e) {
            log.error("WhatsApp notification failed for {}: {}", normalizedPhone, e.getMessage());
        }
    }

    /**
     * Send via Meta WhatsApp Business Cloud API.
     */
    private void sendViaMeta(String phone, String message) throws Exception {
        String url = "https://graph.facebook.com/v18.0/" + phoneNumberId + "/messages";

        String jsonBody = """
                {
                    "messaging_product": "whatsapp",
                    "to": "%s",
                    "type": "text",
                    "text": { "body": "%s" }
                }
                """.formatted(phone, escapeJson(message));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("WhatsApp (Meta) sent successfully to {}", phone);
        } else {
            log.error("WhatsApp (Meta) failed: {} - {}", response.statusCode(), response.body());
        }
    }

    /**
     * Send via Netgsm WhatsApp API (Turkish provider).
     */
    private void sendViaNetgsm(String phone, String message) throws Exception {
        // Netgsm expects Turkish format without +90 prefix
        String turkishPhone = phone.replaceFirst("^\\+?90", "");

        String url = "https://api.netgsm.com.tr/whatsapp/send";
        String jsonBody = """
                {
                    "phone": "%s",
                    "message": "%s"
                }
                """.formatted(turkishPhone, escapeJson(message));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("WhatsApp (Netgsm) response: {} for {}", response.statusCode(), turkishPhone);
    }

    // ── Utilities ──────────────────────────────────────────────

    /**
     * Normalize Turkish phone numbers to international format.
     * Handles: 05xx, 5xx, +905xx, 905xx
     */
    private String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9+]", "");

        if (cleaned.startsWith("+90")) return cleaned;
        if (cleaned.startsWith("90") && cleaned.length() == 12) return "+" + cleaned;
        if (cleaned.startsWith("0") && cleaned.length() == 11) return "+9" + cleaned;
        if (cleaned.length() == 10 && cleaned.startsWith("5")) return "+90" + cleaned;

        return cleaned;
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
