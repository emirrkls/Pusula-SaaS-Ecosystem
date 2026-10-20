package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.WhatsAppIntegrationProperties;
import com.pusula.backend.entity.*;
import com.pusula.backend.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(showSql = false, properties = {
    "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.sql.init.mode=never",
    "whatsapp.onboarding.credential-encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
})
@ContextConfiguration(classes = WhatsAppCoexistencePersistenceTest.Config.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WhatsAppCoexistencePersistenceTest {
    @Configuration(proxyBeanMethods = false) @EnableAutoConfiguration
    @EntityScan("com.pusula.backend.entity")
    @EnableJpaRepositories("com.pusula.backend.repository")
    @Import({WhatsAppCoexistenceSyncStore.class, WhatsAppCoexistenceMirrorService.class,
        WhatsAppCredentialCrypto.class, WhatsAppIntegrationProperties.class, ObjectMapper.class})
    static class Config {}
    @Autowired WhatsAppBusinessIntegrationRepository integrations;
    @Autowired WhatsAppCoexistenceSyncJobRepository jobs;
    @Autowired WhatsAppCoexistenceRecordRepository records;
    @Autowired WhatsAppCoexistenceSyncStore store;
    @Autowired WhatsAppCoexistenceMirrorService mirror;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired ObjectMapper mapper;
    Long id;
    LocalDateTime now = LocalDateTime.now();

    @BeforeEach void setup() {
        id = new TransactionTemplate(transactionManager).execute(tx -> {
            var integration = new WhatsAppBusinessIntegration();
            integration.setCompanyId(10L); integration.setWabaId("12345"); integration.setPhoneNumberId("67890");
            integration.setDisplayPhoneNumber("905550000000"); integration.setAccessTokenCiphertext("not-used-by-test");
            integration.setCoexistence(true); integration.setCoexistenceGeneration("g");
            integration.setCoexistenceStartedAt(now); integration.setStatus("CONNECTED");
            integration.setWebhookSubscriptionStatus("SUBSCRIBED");
            integrations.saveAndFlush(integration); store.enqueue(integration, now); return integration.getId();
        });
    }
    @AfterEach void cleanup() {
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            records.deleteAll(); jobs.deleteAll(); integrations.deleteAll();
        });
    }
    @Test void durableClaimsCallbacksAndTenantScopedReadsWorkTogether() throws Exception {
        var queued = jobs.findByIntegrationIdAndGeneration(id, "g");
        assertEquals(2, queued.size());
        for (var job : queued) {
            assertTrue(store.claim(job.getId(), now.plusSeconds(1)).isPresent());
            assertTrue(store.claim(job.getId(), now.plusSeconds(1)).isEmpty());
        }
        long timestamp = Instant.now().getEpochSecond() - 30;
        var chunk = mapper.readTree("""
            {"metadata":{"phone_number_id":"67890"},"history":[{
              "metadata":{"phase":0,"chunk_order":1,"progress":100},"threads":[{
                "id":"905551111111","messages":[{"id":"wamid.integration","type":"text",
                  "from":"905551111111","timestamp":"%s","text":{"body":"Synthetic private message"}}]
              }]}]}
            """.formatted(timestamp));
        mirror.processChange("12345", mapper.valueToTree(timestamp), "history", chunk);
        mirror.processChange("12345", mapper.valueToTree(timestamp), "history", chunk);
        for (var job : queued) store.outcome(job.getId(), "request-" + job.getId(), null, false, now.plusSeconds(2));
        var integration = integrations.findById(id).orElseThrow();
        assertEquals("READY", store.snapshot(integration).status());
        assertEquals(1, records.count());
        assertFalse(records.findAll().get(0).getPayloadCiphertext().contains("Synthetic"));
        assertEquals(1, mirror.read(10L, "MESSAGE", null, 0).size());
        assertTrue(mirror.read(99L, "MESSAGE", null, 0).isEmpty());
        // Contact acceptance has no final marker and must not turn into an error a day later.
        store.due(now.plusHours(25));
        assertEquals("READY", store.snapshot(integration).status());
    }
}
