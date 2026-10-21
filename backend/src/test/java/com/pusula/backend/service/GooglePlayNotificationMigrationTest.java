package com.pusula.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.DriverManager;
import static org.junit.jupiter.api.Assertions.*;

class GooglePlayNotificationMigrationTest {
    @Test void v51CreatesInboxAndRetryIndexesInPostgresCompatibilityMode() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:play_notification_migration;MODE=PostgreSQL", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V51__google_play_notification_inbox.sql"));
            try (var query = connection.createStatement(); var result = query.executeQuery("SELECT count(*) FROM google_play_notifications")) {
                assertTrue(result.next()); assertEquals(0, result.getInt(1));
            }
        }
    }
    @Test void v52CreatesEncryptedReceiptVaultWithTenantConstraintAndRetryIndexes() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:play_receipt_migration;MODE=PostgreSQL", "sa", "")) {
            connection.createStatement().execute("CREATE TABLE companies (id BIGINT PRIMARY KEY)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V52__google_play_receipt_reconciliation.sql"));
            connection.createStatement().execute("INSERT INTO companies VALUES (10)");
            String insert="INSERT INTO google_play_purchases (id,company_id,product_id,verified_at,next_check_at) VALUES ('receipt',%s,'usta',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)";
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement().execute(insert.formatted(11)));
            connection.createStatement().execute(insert.formatted(10));
            try (var query=connection.createStatement(); var rows=query.executeQuery("SELECT retired,attempts,encrypted_token FROM google_play_purchases")) {
                assertTrue(rows.next()); assertFalse(rows.getBoolean(1)); assertEquals(0,rows.getInt(2)); assertNull(rows.getString(3));
            }
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement().execute(insert.formatted(10)));
        }
    }
}
