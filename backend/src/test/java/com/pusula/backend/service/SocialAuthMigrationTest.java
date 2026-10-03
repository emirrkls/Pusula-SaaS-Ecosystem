package com.pusula.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Executes the actual V43 SQL in an isolated H2 PostgreSQL-mode database, never production. */
class SocialAuthMigrationTest {
    @Test void freePlanMigrationPreservesRestrictionsAndPaidDatesAndEnforcesIdentityUniqueness() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:h2:mem:social_" + UUID.randomUUID() + ";MODE=PostgreSQL");
             Statement sql = connection.createStatement()) {
            sql.execute("CREATE TABLE users(id BIGINT PRIMARY KEY)");
            sql.execute("CREATE TABLE companies(id BIGINT PRIMARY KEY, plan_type VARCHAR(20), subscription_status VARCHAR(20), "
                    + "trial_ends_at TIMESTAMP, subscription_expires_at TIMESTAMP, is_read_only BOOLEAN)");
            sql.execute("INSERT INTO users VALUES(1),(2)");
            sql.execute("INSERT INTO companies VALUES "
                    + "(1,'CIRAK','TRIAL','2020-01-01','2020-01-01',false),"
                    + "(2,'CIRAK','SUSPENDED','2020-01-01','2020-01-01',true),"
                    + "(3,'USTA','TRIAL','2030-01-01','2030-02-01',false),"
                    + "(4,NULL,'TRIAL','2020-01-01','2020-01-01',true)");
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/migration/V43__perpetual_free_plan_and_social_identity.sql"));
            try (var result = sql.executeQuery("SELECT * FROM companies ORDER BY id")) {
                assertTrue(result.next()); assertEquals("ACTIVE", result.getString("subscription_status"));
                assertNull(result.getTimestamp("trial_ends_at")); assertNull(result.getTimestamp("subscription_expires_at"));
                assertFalse(result.getBoolean("is_read_only"));
                assertTrue(result.next()); assertEquals("SUSPENDED", result.getString("subscription_status"));
                assertTrue(result.getBoolean("is_read_only")); assertNull(result.getTimestamp("trial_ends_at"));
                assertTrue(result.next()); assertEquals("TRIAL", result.getString("subscription_status"));
                assertNotNull(result.getTimestamp("trial_ends_at")); assertNotNull(result.getTimestamp("subscription_expires_at"));
                assertTrue(result.next()); assertEquals("CIRAK", result.getString("plan_type"));
                assertTrue(result.getBoolean("is_read_only"));
            }
            try (var result = sql.executeQuery("SELECT local_password_enabled FROM users")) {
                assertTrue(result.next()); assertTrue(result.getBoolean(1));
            }
            sql.execute("INSERT INTO social_auth_identities(provider,subject,user_id) VALUES('GOOGLE','sub',1)");
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO social_auth_identities(provider,subject,user_id) VALUES('GOOGLE','sub',2)"));
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO social_auth_identities(provider,subject,user_id) VALUES('GOOGLE','different',1)"));
            assertThrows(SQLException.class, () -> sql.execute("INSERT INTO social_auth_identities(provider,subject,user_id) VALUES('FAKE','sub',2)"));
            sql.execute("INSERT INTO social_auth_identities(provider,subject,user_id) VALUES('APPLE','sub',1)");
            sql.execute("INSERT INTO apple_auth_challenges VALUES('challenge','nonce',CURRENT_TIMESTAMP,NULL)");
        }
    }
}
