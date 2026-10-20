package com.pusula.backend.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in integration test. Refuses every non-loopback/non-disposable database target. */
@EnabledIfEnvironmentVariable(named = "PUSULA_COEXISTENCE_TEST_DB", matches = ".+")
class WhatsAppCoexistenceMigrationTest {
    Connection connection;
    String schema;
    @BeforeEach void setup() throws Exception {
        String url = System.getenv("PUSULA_COEXISTENCE_TEST_DB");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/coexistence_test"))
            throw new IllegalArgumentException("Only isolated local coexistence_test is allowed");
        connection = DriverManager.getConnection(url, "coex_test", "coex-local-test");
        schema = "coex_" + UUID.randomUUID().toString().replace("-", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            statement.execute("SET search_path TO " + schema);
            statement.execute("CREATE TABLE companies(id BIGINT PRIMARY KEY)");
            statement.execute("CREATE TABLE whatsapp_business_integrations(id BIGSERIAL PRIMARY KEY, company_id BIGINT NOT NULL)");
            statement.execute("CREATE TABLE whatsapp_onboarding_sessions(id BIGSERIAL PRIMARY KEY)");
            statement.execute("INSERT INTO companies VALUES(10),(99)");
            statement.execute("INSERT INTO whatsapp_business_integrations VALUES(22,10)");
        }
        ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V50__whatsapp_coexistence_sync.sql"));
    }
    @AfterEach void cleanup() throws Exception {
        if (connection == null) return;
        try {
            if (schema != null && schema.matches("coex_[a-f0-9]{32}"))
                try (Statement statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
        } finally { connection.close(); }
    }
    @Test void migrationPreservesLegacyIntegrationsAndEnforcesOneJobPerConnectionGeneration() throws Exception {
        try (Statement statement = connection.createStatement()) {
            try (ResultSet result = statement.executeQuery("SELECT coexistence FROM whatsapp_business_integrations WHERE id=22")) {
                assertTrue(result.next()); assertFalse(result.getBoolean(1));
            }
            String insert = """
                INSERT INTO whatsapp_coexistence_sync_jobs
                  (integration_id,generation,sync_type,status,next_attempt_at,deadline_at,updated_at)
                VALUES(22,'generation-a','history','PENDING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + interval '24 hours',CURRENT_TIMESTAMP)
                """;
            assertEquals(1, statement.executeUpdate(insert));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert));
            assertEquals(1, statement.executeUpdate(insert.replace("generation-a", "generation-b")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                "UPDATE whatsapp_coexistence_sync_jobs SET progress=101 WHERE generation='generation-a'"));
            assertThrows(SQLException.class, () -> statement.executeUpdate(
                "UPDATE whatsapp_coexistence_sync_jobs SET status='FAKE_SUCCESS' WHERE generation='generation-a'"));
        }
    }
    @Test void recordKeysDeduplicateWithinGenerationAndCannotReferenceMissingTenant() throws Exception {
        try (Statement statement = connection.createStatement()) {
            String insert = """
                INSERT INTO whatsapp_coexistence_records
                  (integration_id,company_id,generation,kind,source,record_key,payload_ciphertext,event_at,updated_at)
                VALUES(22,10,'generation-a','MESSAGE','ECHO','key','encrypted-only',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """;
            assertEquals(1, statement.executeUpdate(insert));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert));
            assertEquals(1, statement.executeUpdate(insert.replace("generation-a", "generation-b")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert.replace("22,10,", "22,404,").replace("'key'", "'key2'")));
            assertThrows(SQLException.class, () -> statement.executeUpdate(insert.replace("22,10,", "22,99,").replace("'key'", "'key3'")));
        }
    }
}
