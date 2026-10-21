package com.pusula.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import org.springframework.web.util.UriComponentsBuilder;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.ArrayList;
import java.util.Comparator;

@Service
public class GooglePlayVerificationServiceImpl implements GooglePlayVerificationService {

    private static final Logger log = LoggerFactory.getLogger(GooglePlayVerificationServiceImpl.class);

    private static final Set<String> ENTITLED = Set.of("SUBSCRIPTION_STATE_ACTIVE",
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD", "SUBSCRIPTION_STATE_CANCELED");
    private static final Set<String> STATES = Set.of("SUBSCRIPTION_STATE_ACTIVE", "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
            "SUBSCRIPTION_STATE_CANCELED", "SUBSCRIPTION_STATE_ON_HOLD", "SUBSCRIPTION_STATE_PAUSED",
            "SUBSCRIPTION_STATE_EXPIRED", "SUBSCRIPTION_STATE_PENDING", "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED");
    private final String packageName;
    private final GooglePlayAccessTokenProvider tokens;
    private final RestTemplate rest;
    private final Clock clock;

    @Autowired
    public GooglePlayVerificationServiceImpl(@Value("${google.play.package-name:}") String packageName,
                                             GooglePlayAccessTokenProvider tokens) {
        this(packageName, tokens, template(), Clock.systemUTC());
    }
    GooglePlayVerificationServiceImpl(String packageName, GooglePlayAccessTokenProvider tokens,
                                      RestTemplate rest, Clock clock) {
        this.packageName = packageName;
        this.tokens = tokens;
        this.rest = rest;
        this.clock = clock;
    }
    private static RestTemplate template() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(20_000);
        return new RestTemplate(factory);
    }

    @Override
    public GoogleVerificationResult verifySubscription(String purchaseToken, String productId) {
        if (packageName == null || packageName.isBlank()) return failure("PLAY_NOT_CONFIGURED", true);
        if (purchaseToken == null || purchaseToken.isBlank()
                || purchaseToken.length() > 4096
                || (productId != null && !("usta".equals(productId) || "patron".equals(productId))))
            return failure("INVALID_REQUEST", false);
        try {
            var uri = UriComponentsBuilder.fromHttpUrl("https://androidpublisher.googleapis.com")
                    .pathSegment("androidpublisher", "v3", "applications", packageName, "purchases",
                            "subscriptionsv2", "tokens", purchaseToken).build().encode().toUri();
            var headers = new HttpHeaders();
            headers.setBearerAuth(tokens.accessToken());
            var response = rest.exchange(uri, HttpMethod.GET, new HttpEntity<Void>(headers), Map.class);
            Map<?, ?> body = response.getBody();
            if (body == null) return failure("INVALID_GOOGLE_RESPONSE", true);
            String state = String.valueOf(body.get("subscriptionState"));
            if (!STATES.contains(state)) return failure("UNKNOWN_GOOGLE_STATE", true);
            if (!(body.get("lineItems") instanceof List<?> items)) return failure("INVALID_GOOGLE_RESPONSE", true);
            String linked = body.get("linkedPurchaseToken") instanceof String value && !value.isBlank() ? value : null;
            if (linked != null && (linked.length() > 4096 || linked.equals(purchaseToken)))
                return failure("INVALID_GOOGLE_RESPONSE", true);
            // Deferred replacement temporarily contains the paid old item and the upcoming item.
            // Only one item may grant access; concurrent add-ons are intentionally unsupported.
            List<Line> lines = new ArrayList<>();
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> line) || !(line.get("productId") instanceof String id))
                    return failure("INVALID_GOOGLE_RESPONSE", true);
                if (!Set.of("usta", "patron").contains(id)) return failure("UNSUPPORTED_GOOGLE_PRODUCTS", false);
                Instant expiry = line.get("expiryTime") instanceof String value ? Instant.parse(value) : null;
                String base = line.get("offerDetails") instanceof Map<?, ?> offer
                        && offer.get("basePlanId") instanceof String value ? value : null;
                if (base != null && !Set.of(id + "-monthly", id + "-yearly").contains(base))
                    return failure("UNSUPPORTED_GOOGLE_BASE_PLAN", false);
                String deferred = line.get("deferredItemReplacement") instanceof Map<?, ?> future
                        && future.get("productId") instanceof String value ? value : null;
                if (deferred != null && !Set.of("usta", "patron").contains(deferred))
                    return failure("UNSUPPORTED_GOOGLE_PRODUCTS", false);
                lines.add(new Line(id, expiry, base, deferred));
            }
            if (lines.isEmpty() || lines.size() > 2) return failure("UNSUPPORTED_GOOGLE_PRODUCTS", false);
            var current = lines.stream().filter(line -> line.expiry() != null && line.expiry().isAfter(clock.instant())).toList();
            if (current.size() > 1) return failure("UNSUPPORTED_GOOGLE_PRODUCTS", false);
            Line selected = current.isEmpty() ? lines.stream().filter(line -> line.expiry() != null)
                    .max(Comparator.comparing(Line::expiry)).orElse(null) : current.get(0);
            if (selected == null) return failure("PRODUCT_OR_EXPIRY_MISSING", false);
            if (lines.size() == 2 && (linked == null || !validReplacementShape(lines, selected)))
                return failure("UNSUPPORTED_GOOGLE_PRODUCTS", false);
            String actualProduct = selected.product();
            if (productId != null && !productId.equals(actualProduct) && !productId.equals(selected.deferred()))
                return failure("PRODUCT_MISMATCH", false);
            Instant expiry = selected.expiry();
            String account = null;
            if (body.get("externalAccountIdentifiers") instanceof Map<?, ?> identifiers
                    && identifiers.get("obfuscatedExternalAccountId") instanceof String value) account = value;
            boolean valid = ENTITLED.contains(state) && expiry.isAfter(clock.instant());
            return new GoogleVerificationResult(valid, GooglePlayAccountBinding.sha256(purchaseToken), actualProduct,
                    valid ? null : "SUBSCRIPTION_NOT_ENTITLED", expiry, state, false, account,
                    linked == null ? null : GooglePlayAccountBinding.sha256(linked), selected.basePlan(), selected.deferred(),
                    body.get("acknowledgementState") instanceof String value ? value : null);
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            // URLs and exception messages can contain the purchase token. Never log them.
            log.warn("Google Play verification HTTP status={}", status);
            return failure("PLAY_HTTP_" + status, status == 401 || status == 403 || status == 429 || status >= 500);
        } catch (Exception ex) {
            log.warn("Google Play verification unavailable, type={}", ex.getClass().getSimpleName());
            return failure("PLAY_VERIFICATION_UNAVAILABLE", true);
        }
    }

    private boolean validReplacementShape(List<Line> lines, Line selected) {
        Line other = lines.get(0) == selected ? lines.get(1) : lines.get(0);
        return other.expiry() == null ? other.product().equals(selected.deferred())
                : !other.expiry().isAfter(clock.instant());
    }
    private record Line(String product, Instant expiry, String basePlan, String deferred) {}

    private GoogleVerificationResult failure(String reason, boolean retryable) {
        return new GoogleVerificationResult(false, null, null, reason, null, null, retryable, null);
    }
}
