package com.pusula.backend.service;

import com.pusula.backend.entity.GooglePlayPurchase;
import com.pusula.backend.repository.GooglePlayPurchaseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.Optional;

@Service
public class GooglePlayReceiptStore {
    private final GooglePlayPurchaseRepository receipts;
    private final GooglePlayTokenCrypto crypto;
    private final boolean enabled;
    public GooglePlayReceiptStore(GooglePlayPurchaseRepository receipts, GooglePlayTokenCrypto crypto,
            @Value("${google.play.lifecycle.enabled:false}") boolean enabled) {
        this.receipts=receipts; this.crypto=crypto; this.enabled=enabled;
    }
    public boolean enabled() { return enabled; }
    public boolean ready() {
        if (!enabled) return false;
        try { crypto.requireConfigured(); return true; }
        catch (IllegalStateException ex) { return false; }
    }
    public Optional<GooglePlayPurchase> find(String hash) { return receipts.findById(hash); }
    @Transactional
    public void remember(String token, Long companyId, GooglePlayVerificationService.GoogleVerificationResult result) {
        if (!enabled) return;
        crypto.requireConfigured();
        String hash=GooglePlayAccountBinding.sha256(token);
        var row=receipts.lockById(hash).orElseGet(() -> new GooglePlayPurchase(hash, companyId));
        if (!companyId.equals(row.getCompanyId())) throw new PaymentOwnershipException();
        row.verified(crypto.encrypt(token,"receipt:"+hash),result,LocalDateTime.now(ZoneOffset.UTC));
        receipts.saveAndFlush(row);
    }
    public String token(GooglePlayPurchase row) { return crypto.decrypt(row.getEncryptedToken(),"receipt:"+row.getId()); }
    @Transactional
    public void retire(String hash) { receipts.lockById(hash).ifPresent(row -> { row.retire(); receipts.save(row); }); }
    @Transactional
    public void deferred(String hash) { receipts.lockById(hash).filter(row -> !row.isRetired())
            .ifPresent(row -> { row.defer(LocalDateTime.now(ZoneOffset.UTC)); receipts.save(row); }); }
    @Transactional
    public void acknowledged(String hash) { receipts.lockById(hash).filter(row -> !row.isRetired())
            .ifPresent(row -> { row.acknowledge(); receipts.save(row); }); }
}
