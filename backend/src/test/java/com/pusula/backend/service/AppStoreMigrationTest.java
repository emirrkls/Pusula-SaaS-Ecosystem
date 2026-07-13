package com.pusula.backend.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AppStoreMigrationTest {

    @Test
    void migrationIsRepeatableAndLeavesNullOwnershipValuesUnconstrained() throws IOException {
        String migration;
        try (var stream = getClass().getResourceAsStream("/V7__app_store_subscription_verification.sql")) {
            if (stream == null) {
                throw new IOException("V7 migration resource not found");
            }
            migration = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(migration.contains("CREATE UNIQUE INDEX IF NOT EXISTS ux_companies_subscription_provider_external_id"));
        assertTrue(migration.contains("subscription_provider IS NOT NULL"));
        assertTrue(migration.contains("external_subscription_id IS NOT NULL"));
    }
}
