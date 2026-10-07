package com.pusula.backend.service;

import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ContextConfiguration;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.anyString;

@DataJpaTest(properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@ContextConfiguration(classes=AccountAndNotificationPersistenceTest.Configuration.class)
class AccountAndNotificationPersistenceTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @EntityScan(basePackages="com.pusula.backend.entity")
    @EnableJpaRepositories(basePackages="com.pusula.backend.repository")
    @Import({UserAccountDeletionService.class, AppStoreNotificationService.class})
    static class Configuration {}
    @Autowired UserAccountDeletionService deletion;
    @Autowired AppStoreNotificationService notifications;
    @Autowired UserRepository users;
    @Autowired CompanyRepository companies;
    @Autowired AuditLogRepository audits;
    @Autowired AppStoreNotificationRepository events;
    @Autowired SignatureErasureTaskRepository signatures;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @MockBean SocialAccountDeletionService social;
    @MockBean PasswordEncoder passwords;
    @MockBean AppleAppStoreVerificationService verifier;

    @Test void deletionActuallyErasesProfileAndHiddenAuditDataFromDatabase() {
        var company = company();
        var user = new User(); user.setCompanyId(company.getId()); user.setUsername("personal@example.com");
        user.setFullName("Personal Name"); user.setPasswordHash("private-hash"); user.setRole("COMPANY_ADMIN");
        user.setSignaturePath("signatures/1/signature.png"); users.saveAndFlush(user);
        var personal = new AuditLog(company.getId(), user.getId(), "Personal Name", "LOGIN", "AUTH", null, "personal@example.com");
        audits.saveAndFlush(personal);
        var hidden = new AuditLog(company.getId(), user.getId(), "Personal Name", "LOGIN", "AUTH", null, "personal@example.com");
        hidden.setDeleted(true); audits.saveAndFlush(hidden);
        var business = new AuditLog(company.getId(), user.getId(), "Personal Name", "CREATE", "SERVICE_TICKET", 77L, "Business record");
        audits.saveAndFlush(business);
        when(passwords.encode(anyString())).thenReturn("new-random-hash");
        deletion.delete(user);
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_deleted FROM users WHERE id=?", Boolean.class, user.getId()));
        entityManager.clear(); // A subsequent request must not see the managed tombstone from this transaction.
        assertTrue(users.findById(user.getId()).isEmpty());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM audit_logs WHERE user_id=? AND entity_type='AUTH'", Integer.class, user.getId()));
        assertEquals("Silinen kullanıcı", jdbc.queryForObject("SELECT user_name FROM audit_logs WHERE id=?", String.class, business.getId()));
        assertEquals("Business record", jdbc.queryForObject("SELECT description FROM audit_logs WHERE id=?", String.class, business.getId()));
        assertEquals("new-random-hash", jdbc.queryForObject("SELECT password_hash FROM users WHERE id=?", String.class, user.getId()));
        assertNull(jdbc.queryForObject("SELECT signature_path FROM users WHERE id=?", String.class, user.getId()));
        assertEquals(1, signatures.count()); assertEquals(1, companies.count());
    }

    @Test void notificationSnapshotAndReplayAreDurable() {
        var company = company(); company.setSubscriptionProvider("APP_STORE");
        company.setExternalSubscriptionId("appstore:" + AppStoreNotificationService.hash("original"));
        companies.saveAndFlush(company);
        var now = LocalDateTime.now(ZoneOffset.UTC);
        var tx = new AppleAppStoreVerificationService.AppleVerificationResult("tx", "original", "com.pusula.usta",
                PlanType.USTA, "com.pusula.service", "Sandbox", now.minusDays(1), now.plusDays(29));
        var payload = new AppleAppStoreVerificationService.AppleNotificationResult(UUID.randomUUID().toString(),
                "DID_RENEW", null, "Sandbox", Instant.now().toEpochMilli(), tx, false, "ACTIVE", null);
        when(verifier.verifyNotification("verified")).thenReturn(payload);
        notifications.receive("verified"); notifications.receive("verified");
        assertEquals(1, events.count());
        assertEquals("PROCESSED", events.findById(payload.notificationId()).orElseThrow().getProcessingStatus());
        assertEquals(PlanType.USTA, companies.findById(company.getId()).orElseThrow().getPlanType());
        assertEquals("Sandbox", companies.findById(company.getId()).orElseThrow().getAppStoreEnvironment());
    }

    private Company company() {
        var c = new Company(); c.setName("Company"); c.setSubscriptionStatus("ACTIVE");
        return companies.saveAndFlush(c);
    }
}
