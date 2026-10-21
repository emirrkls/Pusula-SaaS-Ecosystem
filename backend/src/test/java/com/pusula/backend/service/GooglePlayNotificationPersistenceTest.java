package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.repository.GooglePlayNotificationRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.transaction.TestTransaction;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DataJpaTest(showSql=false, properties={"spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
@ContextConfiguration(classes=GooglePlayNotificationPersistenceTest.Configuration.class)
class GooglePlayNotificationPersistenceTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    @EnableAutoConfiguration
    @EntityScan(basePackages="com.pusula.backend.entity")
    @EnableJpaRepositories(basePackages="com.pusula.backend.repository")
    static class Configuration {
        @Bean GooglePlayNotificationSettings settings() { return new GooglePlayNotificationSettings(true,
                "https://example.test/callback", "push@example.test", "projects/test/subscriptions/play", "com.pusula.service"); }
        @Bean GooglePlayTokenCrypto crypto() { return new GooglePlayTokenCrypto(Base64.getEncoder().encodeToString(new byte[32])); }
        @Bean GooglePlayNotificationService service(GooglePlayNotificationSettings settings, GooglePlayTokenCrypto crypto,
                GooglePlayNotificationRepository inbox, SubscriptionService subscriptions) {
            return new GooglePlayNotificationService(settings, crypto, inbox, subscriptions, new ObjectMapper());
        }
    }
    @Autowired GooglePlayNotificationRepository inbox;
    @Autowired GooglePlayNotificationService service;
    @Autowired EntityManager entityManager;
    @MockBean SubscriptionService subscriptions;

    byte[] body() {
        String payload = "{\"packageName\":\"com.pusula.service\",\"eventTimeMillis\":\"" + Instant.now().toEpochMilli()
                + "\",\"subscriptionNotification\":{\"notificationType\":2,\"purchaseToken\":\"private-test-receipt\"}}";
        return ("{\"subscription\":\"projects/test/subscriptions/play\",\"message\":{\"messageId\":\"message-1\",\"data\":\""
                + Base64.getEncoder().encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "\"}}").getBytes(StandardCharsets.UTF_8);
    }
    @Test void inboxSurvivesContextClearAndDuplicateDeliveryAndClearsTokenOnCompletion() {
        byte[] body = body(); service.receive(body); inbox.flush(); entityManager.clear();
        TestTransaction.flagForCommit(); TestTransaction.end(); TestTransaction.start();
        service.receive(body); assertEquals(1, inbox.count());
        var due = inbox.findTop20ByProcessingStatusInAndNextRetryAtLessThanEqualOrderByNextRetryAtAsc(
                List.of("PENDING"), LocalDateTime.now(ZoneOffset.UTC));
        assertEquals(1, due.size()); assertFalse(due.get(0).getEncryptedToken().contains("private-test-receipt"));
        when(subscriptions.reconcileGoogleNotification("private-test-receipt")).thenReturn("APPLIED");
        service.process("message-1"); inbox.flush(); entityManager.clear();
        var completed = inbox.findById("message-1").orElseThrow();
        assertEquals("APPLIED", completed.getProcessingStatus()); assertNull(completed.getEncryptedToken());
        assertEquals(0, inbox.deleteFinishedBefore(LocalDateTime.now(ZoneOffset.UTC).minusDays(30)));
        assertEquals(1, inbox.deleteFinishedBefore(LocalDateTime.now(ZoneOffset.UTC).plusSeconds(1)));
    }
}
