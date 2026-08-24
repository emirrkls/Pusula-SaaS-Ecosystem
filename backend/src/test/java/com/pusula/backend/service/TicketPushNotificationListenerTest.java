package com.pusula.backend.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.config.ApplePushProperties;
import com.pusula.backend.entity.PushDevice;
import com.pusula.backend.entity.PushEnvironment;
import com.pusula.backend.entity.PushPlatform;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.event.TicketAssignedEvent;
import com.pusula.backend.repository.PushDeviceRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.time.LocalDateTime;

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
        ServiceTicketRepository ticketRepository = dueTicketRepository();
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
                repository, ticketRepository, crypto, gateway, properties, new ObjectMapper(), "Europe/Istanbul");

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
        ServiceTicketRepository ticketRepository = dueTicketRepository();
        PushTokenCrypto crypto = mock(PushTokenCrypto.class);
        ApnsGateway gateway = mock(ApnsGateway.class);
        PushDevice device = device();
        when(repository.findByCompanyIdAndUserIdAndActiveTrueAndPlatform(10L, 7L, PushPlatform.IOS))
                .thenReturn(List.of(device));
        when(crypto.decrypt("ciphertext")).thenReturn(TOKEN);
        when(gateway.send(anyString(), org.mockito.ArgumentMatchers.eq(PushEnvironment.SANDBOX), anyString()))
                .thenReturn(ApnsDeliveryResult.rejected("Unregistered"));
        TicketPushNotificationListener listener = new TicketPushNotificationListener(
                repository, ticketRepository, crypto, gateway, enabledProperties(), new ObjectMapper(), "Europe/Istanbul");

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
                repository, mock(ServiceTicketRepository.class), mock(PushTokenCrypto.class), gateway,
                properties, new ObjectMapper(), "Europe/Istanbul");
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

    @Test
    void assignmentMoreThan24HoursAwayWaitsWithoutSendingOrMarking() {
        PushDeviceRepository devices = mock(PushDeviceRepository.class);
        ServiceTicketRepository tickets = mock(ServiceTicketRepository.class);
        ServiceTicket ticket = ServiceTicket.builder()
                .id(100L).companyId(10L).assignedTechnicianId(7L)
                .status(ServiceTicket.TicketStatus.ASSIGNED)
                .scheduledDate(LocalDateTime.now().plusHours(25)).build();
        when(tickets.findByIdAndCompanyIdForUpdate(100L, 10L)).thenReturn(Optional.of(ticket));
        TicketPushNotificationListener listener = new TicketPushNotificationListener(
                devices, tickets, mock(PushTokenCrypto.class), mock(ApnsGateway.class),
                enabledProperties(), new ObjectMapper(), "Europe/Istanbul");

        listener.onTicketAssigned(new TicketAssignedEvent(10L, 7L, 100L));

        verify(devices, never()).findByCompanyIdAndUserIdAndActiveTrueAndPlatform(
                10L, 7L, PushPlatform.IOS);
        verify(tickets, never()).save(ticket);
    }

    private ServiceTicketRepository dueTicketRepository() {
        ServiceTicketRepository repository = mock(ServiceTicketRepository.class);
        ServiceTicket ticket = ServiceTicket.builder()
                .id(100L).companyId(10L).assignedTechnicianId(7L)
                .status(ServiceTicket.TicketStatus.ASSIGNED).build();
        when(repository.findByIdAndCompanyIdForUpdate(100L, 10L)).thenReturn(Optional.of(ticket));
        when(repository.save(ticket)).thenReturn(ticket);
        return repository;
    }
}
