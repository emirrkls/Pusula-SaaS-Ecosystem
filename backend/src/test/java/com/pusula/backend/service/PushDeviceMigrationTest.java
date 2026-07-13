package com.pusula.backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PushDeviceMigrationTest {
    @Test
    void migrationContainsEncryptedTokenAndTenantRecipientIndexes() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream("/V8__ios_apns_push_devices.sql")) {
            if (stream == null) {
                throw new IOException("V8 push-device migration resource not found");
            }
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(migration.contains("token_hash VARCHAR(64) NOT NULL"));
        assertTrue(migration.contains("token_ciphertext TEXT NOT NULL"));
        assertTrue(migration.contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_push_devices_token_hash"));
        assertTrue(migration.contains("company_id, user_id, active, platform"));
        assertTrue(migration.contains("environment IN ('SANDBOX', 'PRODUCTION')"));
    }
}
