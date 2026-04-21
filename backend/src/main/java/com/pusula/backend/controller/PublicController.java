package com.pusula.backend.controller;

import com.pusula.backend.dto.PublicServiceRequestDTO;
import com.pusula.backend.dto.ServiceTicketDTO;
import com.pusula.backend.service.ServiceTicketService;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Kimlik doğrulaması gerektirmeyen public endpoint'ler.
 * Web sitesindeki iletişim formundan gelen servis taleplerini karşılar.
 * 
 * Güvenlik katmanları:
 * 1. Rate Limiting → RateLimitInterceptor (IP başına 3 istek/dakika)
 * 2. Input Validation → Jakarta @Valid annotations
 * 3. Honeypot → Bot tuzağı alanı kontrolü
 */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private static final Logger log = LoggerFactory.getLogger(PublicController.class);

    private final ServiceTicketService service;

    public PublicController(ServiceTicketService service) {
        this.service = service;
    }

    /**
     * Web sitesi iletişim formundan gelen servis talebi.
     * 
     * Akış:
     * 1. Honeypot kontrolü → Bot ise 200 döndür (bot'u kandırmak için)
     * 2. Validation → @Valid ile otomatik kontrol
     * 3. Müşteri bul/oluştur → Telefon numarasına göre
     * 4. İş emri oluştur → PENDING statüsüyle
     * 
     * @param request Form verileri (Ad, Telefon, Adres, Cihaz Tipi, Açıklama)
     * @return Oluşturulan iş emri bilgileri
     */
    @PostMapping("/service-request")
    public ResponseEntity<?> createServiceRequest(
            @Valid @RequestBody PublicServiceRequestDTO request) {

        // --- Honeypot Kontrolü ---
        // Bot'lar gizli alanı doldurur. Gerçek kullanıcılar görmez.
        // 200 OK döndürüyoruz ki bot başarılı olduğunu sansın ve tekrar denemesin.
        if (request.getWebsite() != null && !request.getWebsite().isBlank()) {
            log.warn("Honeypot tetiklendi — olası bot isteği engellendi. Website alanı: {}",
                    request.getWebsite());
            return ResponseEntity.ok(Map.of(
                    "message", "Talebiniz başarıyla alındı.",
                    "status", "PENDING"));
        }

        try {
            // İş emri oluştur (Service katmanı müşteri bul/oluştur + ticket yarat)
            ServiceTicketDTO result = service.createPublicTicket(request);

            log.info("Web formundan yeni servis talebi oluşturuldu — Ticket ID: {}, Müşteri: {}",
                    result.getId(), request.getCustomerName());

            // Frontend'e sadece gerekli bilgileri döndür (güvenlik: iç detayları ifşa etme)
            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "message", "Servis talebiniz başarıyla oluşturuldu. En kısa sürede sizinle iletişime geçeceğiz.",
                    "ticketId", result.getId(),
                    "status", "PENDING"));

        } catch (Exception e) {
            log.error("Web formu servis talebi oluşturulurken hata: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "error", "Talebiniz oluşturulurken bir hata meydana geldi. Lütfen telefonla iletişime geçin."));
        }
    }
}
