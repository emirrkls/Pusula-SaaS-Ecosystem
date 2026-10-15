package com.pusula.backend.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.MutationReceiptRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.function.*;

@Service
public class ReliableMutationService {
    private final MutationReceiptRepository receipts;
    private final ObjectMapper mapper;
    public ReliableMutationService(MutationReceiptRepository receipts, ObjectMapper mapper) {
        this.receipts = receipts; this.mapper = mapper;
    }

    /** The PostgreSQL unique-key claim, business write and result ID share one transaction. */
    @Transactional
    public <T> T execute(String requestKey, String operation, Object input,
                         Supplier<T> mutation, Function<T, Long> resourceId, LongFunction<T> reload) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof User user)
                || user.getCompanyId() == null || user.getId() == null) {
            throw new AccessDeniedException("Kayıt işlemi için oturum gereklidir.");
        }
        String key = canonicalKey(requestKey);
        String fingerprint = fingerprint(input);
        Instant now = Instant.now();
        // A conflicting insert waits for the first transaction to COMMIT or ROLLBACK.
        // It does not catch a unique violation inside a poisoned JPA transaction.
        receipts.claim(user.getCompanyId(), user.getId(), key, operation, fingerprint, now);
        var receipt = receipts.lockReceipt(user.getCompanyId(), user.getId(), key).orElseThrow();
        if (!receipt.getOperation().equals(operation) || !receipt.getFingerprint().equals(fingerprint)) {
            throw new MutationConflictException("IDEMPOTENCY_MISMATCH",
                    "Bu denemenin içeriği değişmiş. Önceki kaydı kontrol edip yeni bir işlem başlatın.");
        }
        if (receipt.getCreatedAt().isBefore(now.minus(Duration.ofHours(24)))) {
            throw new MutationConflictException("IDEMPOTENCY_EXPIRED",
                    "Kayıt denemesinin güvenli tekrar süresi dolmuş. Önceki kaydı kontrol edin.");
        }
        if (receipt.getResourceId() != null) {
            T value = reload.apply(receipt.getResourceId());
            if (value == null) throw new MutationConflictException("IDEMPOTENCY_RESULT_UNAVAILABLE",
                    "Önceki işlem tamamlanmış ancak kayıt artık erişilebilir değil. Yeni kayıt oluşturulmadı.");
            return value;
        }
        T value = mutation.get();
        Long id = resourceId.apply(value);
        if (id == null || id <= 0) throw new IllegalStateException("Kayıt sonucu doğrulanamadı.");
        receipt.setResourceId(id);
        receipts.saveAndFlush(receipt);
        return value;
    }

    static String canonicalKey(String value) {
        if (value == null || !value.matches("[a-fA-F0-9]{8}(?:-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}")) {
            throw new IllegalArgumentException("Geçerli bir Idempotency-Key gereklidir.");
        }
        return UUID.fromString(value).toString();
    }

    public String fingerprint(Object value) {
        try { return hex(sha256().digest(mapper.writeValueAsBytes(canonical(mapper.valueToTree(value))))); }
        catch (IOException exception) { throw new IllegalArgumentException("Kayıt içeriği doğrulanamadı."); }
    }

    private JsonNode canonical(JsonNode value) {
        if (value.isObject()) {
            ObjectNode result = mapper.createObjectNode();
            TreeMap<String, JsonNode> sorted = new TreeMap<>();
            value.fields().forEachRemaining(field -> sorted.put(field.getKey(), field.getValue()));
            sorted.forEach((key, child) -> result.set(key, canonical(child)));
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(child -> result.add(canonical(child)));
            return result;
        }
        return value;
    }

    public String fileFingerprint(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) {
            throw new IllegalArgumentException("Görsel boş veya 5 MB sınırından büyük.");
        }
        MessageDigest digest = sha256();
        try (InputStream stream = file.getInputStream()) {
            byte[] buffer = new byte[8192]; int read; long total = 0;
            while ((read = stream.read(buffer)) != -1) {
                total += read;
                if (total > 5L * 1024 * 1024) throw new IllegalArgumentException("Görsel 5 MB sınırından büyük.");
                digest.update(buffer, 0, read);
            }
            return hex(digest.digest());
        } catch (IOException exception) { throw new IllegalArgumentException("Görsel okunamadı."); }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }
    private static String hex(byte[] bytes) { return HexFormat.of().formatHex(bytes); }

    /** Keep a 30-day safety tombstone, much longer than the documented 24-hour retry window. */
    @Scheduled(fixedDelayString = "${app.mutation-receipts.cleanup-delay-ms:86400000}", initialDelay = 60000)
    @Transactional
    public void pruneReceipts() { receipts.deleteExpired(Instant.now().minus(Duration.ofDays(30))); }
}
