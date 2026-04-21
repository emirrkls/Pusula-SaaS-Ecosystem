package com.pusula.backend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a tenant exceeds their plan's quota (e.g., max tickets/month).
 * Returns HTTP 429 with usage details.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class QuotaExceededException extends RuntimeException {
    private final String usageType;
    private final int limit;
    private final int current;

    public QuotaExceededException(String usageType, int limit, int current) {
        super("Kota aşıldı: " + usageType + " limiti " + limit + ", mevcut kullanım " + current + ". " +
              "Paketinizi yükselterek limiti artırabilirsiniz.");
        this.usageType = usageType;
        this.limit = limit;
        this.current = current;
    }

    public String getUsageType() { return usageType; }
    public int getLimit() { return limit; }
    public int getCurrent() { return current; }
}
