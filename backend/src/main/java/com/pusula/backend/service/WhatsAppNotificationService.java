package com.pusula.backend.service;

import com.pusula.backend.annotation.RequiresFeature;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.ServiceTicket;
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
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    @Value("${whatsapp.api.graph-version:v26.0}")
    private String graphApiVersion;

    @Value("${whatsapp.api.allowed-company-ids:}")
    private String allowedCompanyIds;

    @Value("${whatsapp.api.template-language:tr}")
    private String templateLanguage;

    @Value("${whatsapp.api.template-service-created:pusula_service_created}")
    private String serviceCreatedTemplate;

    @Value("${whatsapp.api.template-service-completed:pusula_service_completed}")
    private String serviceCompletedTemplate;

    private static final DateTimeFormatter SERVICE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.forLanguageTag("tr-TR"));

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public WhatsAppNotificationService(CustomerRepository customerRepository,
                                       ServiceTicketRepository ticketRepository) {
        this.customerRepository = customerRepository;
        this.ticketRepository = ticketRepository;
    }

    /** Notify the customer when a new service work order is created. */
    @RequiresFeature("WHATSAPP_INTEGRATION")
    public void notifyServiceCreated(Long ticketId) {
        ServiceTicket ticket = findEligibleTicket(ticketId);
        if (ticket == null) return;

        Customer customer = findEligibleCustomer(ticket);
        if (customer == null) return;

        String scheduledAt = ticket.getScheduledDate() == null
                ? "Planlanacak"
                : ticket.getScheduledDate().format(SERVICE_DATE_FORMAT);
        String description = summarize(ticket.getDescription(), "Servis talebi", 120);
        String fallback = buildCreationMessage(customer.getName(), ticketId, scheduledAt, description);
        sendNotification(customer.getPhone(), serviceCreatedTemplate,
                List.of(customer.getName(), String.valueOf(ticketId), scheduledAt, description), fallback);
    }

    /**
     * Send service completion notification to customer.
     * Called after technician completes a ticket and processes payment.
     */
    @RequiresFeature("WHATSAPP_INTEGRATION")
    public void notifyServiceCompleted(Long ticketId, BigDecimal collectedAmount, BigDecimal remainingDebt) {
        ServiceTicket ticket = findEligibleTicket(ticketId);
        if (ticket == null) return;

        Customer customer = findEligibleCustomer(ticket);
        if (customer == null) return;

        String message = buildCompletionMessage(customer.getName(), ticketId, collectedAmount, remainingDebt);
        sendNotification(customer.getPhone(), serviceCompletedTemplate,
                List.of(customer.getName(), String.valueOf(ticketId), money(collectedAmount), money(remainingDebt)),
                message);
    }

    /**
     * Send cari (current account) update notification.
     * Called when debt is added or reduced on a customer's account.
     */
    @RequiresFeature("WHATSAPP_INTEGRATION")
    public void notifyCariUpdate(Long customerId, BigDecimal newBalance) {
        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null || !isCompanyAllowed(customer.getCompanyId()) || customer.getPhone() == null) return;

        String message = buildCariMessage(customer.getName(), newBalance);
        sendNotification(customer.getPhone(), "", List.of(), message);
    }

    // ── Message Templates ──────────────────────────────────────

    private String buildCreationMessage(String customerName, Long ticketId,
                                        String scheduledAt, String description) {
        return "Sayın %s, %d numaralı servis iş emriniz oluşturulmuştur. Planlanan tarih: %s. İşlem: %s"
                .formatted(customerName, ticketId, scheduledAt, description);
    }

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

    private void sendNotification(String phone, String templateName, List<String> parameters, String fallbackMessage) {
        String normalizedPhone = normalizePhone(phone);

        if (normalizedPhone.isBlank()) {
            log.warn("WhatsApp notification skipped: invalid phone number");
            return;
        }

        if (!apiEnabled) {
            // Mock mode — log to console
            log.info("[MOCK WhatsApp] To: {} | Template: {} | Message: {}",
                    maskPhone(normalizedPhone), templateName, fallbackMessage);
            return;
        }

        if (apiToken == null || apiToken.isBlank() || phoneNumberId == null || phoneNumberId.isBlank()) {
            log.error("WhatsApp notification skipped: API credentials are not configured");
            return;
        }

        try {
            if ("NETGSM".equalsIgnoreCase(provider)) {
                sendViaNetgsm(normalizedPhone, fallbackMessage);
            } else {
                if (templateName == null || templateName.isBlank()) {
                    log.warn("WhatsApp notification skipped: Meta template is not configured for this event");
                    return;
                }
                sendViaMeta(normalizedPhone, templateName, parameters);
            }
        } catch (Exception e) {
            log.error("WhatsApp notification failed for {}: {}", maskPhone(normalizedPhone), e.getMessage());
        }
    }

    /**
     * Send via Meta WhatsApp Business Cloud API.
     */
    private void sendViaMeta(String phone, String templateName, List<String> parameters) throws Exception {
        String url = "https://graph.facebook.com/" + graphApiVersion + "/" + phoneNumberId + "/messages";
        String jsonBody = buildMetaTemplatePayload(phone, templateName, parameters);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            log.info("WhatsApp (Meta) template {} sent successfully to {}", templateName, maskPhone(phone));
        } else {
            log.error("WhatsApp (Meta) template {} failed: HTTP {} - {}",
                    templateName, response.statusCode(), summarize(response.body(), "empty response", 500));
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
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + apiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("WhatsApp (Netgsm) response: {} for {}", response.statusCode(), maskPhone(turkishPhone));
    }

    // ── Utilities ──────────────────────────────────────────────

    /**
     * Normalize Turkish phone numbers to international format.
     * Handles: 05xx, 5xx, +905xx, 905xx
     */
    String normalizePhone(String phone) {
        if (phone == null) return "";
        String cleaned = phone.replaceAll("[^0-9+]", "");

        if (cleaned.startsWith("+90")) return isValidE164(cleaned) ? cleaned : "";
        if (cleaned.startsWith("90") && cleaned.length() == 12) return "+" + cleaned;
        if (cleaned.startsWith("0") && cleaned.length() == 11) return "+9" + cleaned;
        if (cleaned.length() == 10 && cleaned.startsWith("5")) return "+90" + cleaned;

        return isValidE164(cleaned) ? cleaned : "";
    }

    private boolean isValidE164(String phone) {
        return phone != null && phone.matches("^\\+[1-9]\\d{7,14}$");
    }

    String buildMetaTemplatePayload(String phone, String templateName, List<String> parameters) {
        String parameterJson = parameters.stream()
                .map(value -> "{\"type\":\"text\",\"text\":\"" + escapeJson(value) + "\"}")
                .collect(Collectors.joining(","));
        return """
                {"messaging_product":"whatsapp","recipient_type":"individual","to":"%s","type":"template",\
"template":{"name":"%s","language":{"code":"%s"},"components":[{"type":"body","parameters":[%s]}]}}
                """.formatted(phone.replace("+", ""), escapeJson(templateName),
                        escapeJson(templateLanguage), parameterJson).trim();
    }

    private ServiceTicket findEligibleTicket(Long ticketId) {
        ServiceTicket ticket = ticketRepository.findById(ticketId).orElse(null);
        if (ticket == null || ticket.getCustomerId() == null) {
            log.warn("WhatsApp notification skipped: ticket {} not found or no customer", ticketId);
            return null;
        }
        if (!isCompanyAllowed(ticket.getCompanyId())) {
            log.debug("WhatsApp notification skipped: company {} is not in pilot allowlist", ticket.getCompanyId());
            return null;
        }
        return ticket;
    }

    private Customer findEligibleCustomer(ServiceTicket ticket) {
        Customer customer = customerRepository.findById(ticket.getCustomerId()).orElse(null);
        if (customer == null || !Objects.equals(ticket.getCompanyId(), customer.getCompanyId())
                || customer.getPhone() == null || customer.getPhone().isBlank()) {
            log.warn("WhatsApp notification skipped: customer missing, tenant mismatch, or no phone number");
            return null;
        }
        return customer;
    }

    boolean isCompanyAllowed(Long companyId) {
        if (companyId == null || allowedCompanyIds == null || allowedCompanyIds.isBlank()) return false;
        Set<Long> ids = Arrays.stream(allowedCompanyIds.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .flatMap(value -> {
                    try {
                        return java.util.stream.Stream.of(Long.valueOf(value));
                    } catch (NumberFormatException e) {
                        log.warn("Ignoring invalid WhatsApp pilot company id");
                        return java.util.stream.Stream.empty();
                    }
                })
                .collect(Collectors.toSet());
        return ids.contains(companyId);
    }

    private String money(BigDecimal amount) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        return String.format(Locale.forLanguageTag("tr-TR"), "%.2f", safe);
    }

    private String summarize(String value, String fallback, int maxLength) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.isBlank()) normalized = fallback;
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength - 1) + "…";
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 4) return "***";
        return "***" + phone.substring(phone.length() - 4);
    }

    private String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}
