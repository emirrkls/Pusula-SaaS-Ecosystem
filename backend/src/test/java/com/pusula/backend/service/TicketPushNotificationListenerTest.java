package com.pusula.backend.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushEnvironment;
import com.pusula.backend.entity.PushPlatform;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.PushDeviceRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TicketPushNotificationListenerTest {
    private static final String TOKEN = "f0".repeat(32);

    @Test
    void apnsFailureDoesNotEscapeOrLeakPlaintextTokenToLogs() {
        PushDeviceRepository repository = mock(PushDeviceRepository.class);
        PushTokenCrypto crypto = mock(PushTokenCrypto.class);
        ApnsGateway gateway = mock(ApnsGateway.class);
        ApplePushProperties properties = enabledProperties();
        PushDevice device = device();
        when(repository.findByCompanyIdAndUserIdAndActiveTrueAndPlatform(10L, 7L, PushPlatform.IOS))
                .thenReturn(List.of(device));
        when(crypto.decrypt("ciphertext")).thenReturn(TOKEN);
        when(gateway.send(anyString(), org.mockito.ArgumentMatchers.eq(PushEnvironment.SANDBOX), anyString()))
                .thenThrow(new IllegalStateException("provider echoed " + TOKEN));
        TicketPushNotificationListener listener = new TicketPushNotificationListener(
                repository, crypto, gateway, properties, new ObjectMapper());

        Logger logger = (Logger) LoggerFactory.getLogger(TicketPushNotificationListener.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertDoesNotThrow(() -> listener.onTicketAssigned(new TicketAssignedEvent(10L, 7L, 100L)));
            String logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .reduce("", (a, b) -> a + b);
            assertFalse(logs.contains(TOKEN));
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void invalidApnsTokenIsDeactivated() {
        PushDeviceRepository repository = mock(PushDeviceRepository.class);
        PushTokenCrypto crypto = mock(PushTokenCrypto.class);
        ApnsGateway gateway = mock(ApnsGateway.class);
        PushDevice device = device();
        when(repository.findByCompanyIdAndUserIdAndActiveTrueAndPlatform(10L, 7L, PushPlatform.IOS))
                .thenReturn(List.of(device));
        when(crypto.decrypt("ciphertext")).thenReturn(TOKEN);
        when(gateway.send(anyString(), org.mockito.ArgumentMatchers.eq(PushEnvironment.SANDBOX), anyString()))
                .thenReturn(ApnsDeliveryResult.rejected("Unregistered"));
        TicketPushNotificationListener listener = new TicketPushNotificationListener(
                repository, crypto, gateway, enabledProperties(), new ObjectMapper());

        listener.onTicketAssigned(new TicketAssignedEvent(10L, 7L, 100L));

        assertFalse(device.isActive());
        verify(repository).save(device);
    }

    @Test
    void disabledConfigurationNeedsNoCredentialsAndDoesNoWork() {
        ApplePushProperties properties = new ApplePushProperties();
        properties.setEnabled(false);
        PushyApnsGateway gateway = assertDoesNotThrow(() -> new PushyApnsGateway(properties));
        assertTrue(gateway.send(TOKEN, PushEnvironment.SANDBOX, "{}").disabled());

        PushDeviceRepository repository = mock(PushDeviceRepository.class);
        TicketPushNotificationListener listener = new TicketPushNotificationListener(
                repository, mock(PushTokenCrypto.class), gateway, properties, new ObjectMapper());
        listener.onTicketAssigned(new TicketAssignedEvent(10L, 7L, 100L));
        verify(repository, never()).findByCompanyIdAndUserIdAndActiveTrueAndPlatform(
                10L, 7L, PushPlatform.IOS);
    }

    private ApplePushProperties enabledProperties() {
        ApplePushProperties properties = new ApplePushProperties();
        properties.setEnabled(true);
        properties.setBundleId("com.pusula.service");
        return properties;
    }

    private PushDevice device() {
        PushDevice device = new PushDevice();
        device.setId(1L);
        device.setCompanyId(10L);
        device.setUserId(7L);
        device.setEnvironment(PushEnvironment.SANDBOX);
        device.setPlatform(PushPlatform.IOS);
        device.setTokenCiphertext("ciphertext");
        device.setActive(true);
        return device;
    }
}
