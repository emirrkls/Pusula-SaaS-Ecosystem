package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.repository.PushDeviceRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class TicketPushNotificationListenerContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void springContextAcceptsTransactionalEventListenerConfiguration() {
        contextRunner.run(context -> {
            assertDoesNotThrow(() -> context.getBean(TicketPushNotificationListener.class));
            assertNotNull(context.getBean(TicketPushNotificationListener.class));
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        TicketPushNotificationListener listener() {
            ApplePushProperties properties = new ApplePushProperties();
            properties.setEnabled(false);
            return new TicketPushNotificationListener(
                    mock(PushDeviceRepository.class),
                    mock(ServiceTicketRepository.class),
                    mock(PushTokenCrypto.class),
                    mock(ApnsGateway.class),
                    properties,
                    new ObjectMapper(),
                    "Europe/Istanbul");
        }

        @Bean
        PlatformTransactionManager transactionManager() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
