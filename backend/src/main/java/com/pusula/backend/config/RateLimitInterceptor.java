package com.pusula.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IP tabanlı Rate Limiting Interceptor.
 * 
 * Sadece /api/public/** endpoint'lerine uygulanır.
 * Aynı IP'den dakikada maksimum {@link #MAX_REQUESTS_PER_MINUTE} istek kabul eder.
 * 
 * Strateji: In-memory ConcurrentHashMap ile basit sliding window.
 * Tek sunucu (VPS) yapısında Redis'e gerek yok.
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RateLimitInterceptor.class);

    /** Dakika başına izin verilen maksimum istek sayısı */
    private static final int MAX_REQUESTS_PER_MINUTE = 3;

    /** Pencere süresi: 60 saniye (milisaniye cinsinden) */
    private static final long WINDOW_MS = 60_000L;

    /**
     * IP → RequestBucket eşlemesi.
     * ConcurrentHashMap thread-safe erişim sağlar.
     */
    private final Map<String, RequestBucket> requestCounts = new ConcurrentHashMap<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {

        // OPTIONS (preflight) isteklerini atla — CORS uyumluluğu
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String clientIp = extractClientIp(request);

        RequestBucket bucket = requestCounts.compute(clientIp, (ip, existing) -> {
            long now = System.currentTimeMillis();

            if (existing == null || now - existing.windowStart > WINDOW_MS) {
                // Yeni pencere başlat
                return new RequestBucket(now, new AtomicInteger(1));
            }

            // Mevcut pencerede sayacı artır
            existing.count.incrementAndGet();
            return existing;
        });

        if (bucket.count.get() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("Rate limit aşıldı — IP: {}, İstek sayısı: {}", clientIp, bucket.count.get());

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"error\":\"Çok fazla istek gönderdiniz. Lütfen bir dakika sonra tekrar deneyin.\"}");
            return false;
        }

        return true;
    }

    /**
     * İstemcinin gerçek IP adresini çıkarır.
     * Reverse proxy (Nginx) arkasındaki gerçek IP için X-Forwarded-For header'ını kontrol eder.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // İlk IP gerçek istemci IP'sidir (proxy zincirinde)
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Zaman pencereli istek sayacı.
     */
    private static class RequestBucket {
        final long windowStart;
        final AtomicInteger count;

        RequestBucket(long windowStart, AtomicInteger count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
