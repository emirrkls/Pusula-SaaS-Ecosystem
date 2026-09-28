package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.Customer;
import com.pusula.backend.entity.PaymentMethod;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.entity.WhatsAppConsentSource;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import com.pusula.backend.repository.WhatsAppMessageOutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock ServiceTicketRepository ticketRepository;
    @Mock WhatsAppMessageOutboxRepository outboxRepository;

    private WhatsAppNotificationService service;

    @BeforeEach
    void setUp() {
        service = new WhatsAppNotificationService(customerRepository, ticketRepository);
        ReflectionTestUtils.setField(service, "allowedCompanyIds", "7, 10");
        ReflectionTestUtils.setField(service, "templateLanguage", "tr");
    }

    @Test
    void normalizesCommonTurkishPhoneFormats() {
        assertEquals("+905551112233", service.normalizePhone("0555 111 22 33"));
        assertEquals("+905551112233", service.normalizePhone("5551112233"));
        assertEquals("+905551112233", service.normalizePhone("90 555 111 22 33"));
        assertEquals("", service.normalizePhone("1111111111"));
    }

    @Test
    void pilotAllowlistFailsClosedAndIgnoresInvalidEntries() {
        assertTrue(service.isCompanyAllowed(7L));
        assertTrue(service.isCompanyAllowed(10L));
        assertFalse(service.isCompanyAllowed(11L));

        ReflectionTestUtils.setField(service, "allowedCompanyIds", "");
        assertFalse(service.isCompanyAllowed(7L));
    }

    @Test
    void buildsMetaUtilityTemplatePayloadWithoutExposingPlusPrefix() throws Exception {
        String payload = service.buildMetaTemplatePayload("+905551112233", "pusula_service_created",
                List.of("Ayşe \"Test\"", "123", "28.08.2026 14:00", "Klima arızası"));

        assertTrue(payload.contains("\"to\":\"905551112233\""));
        assertTrue(payload.contains("\"name\":\"pusula_service_created\""));
        assertTrue(payload.contains("\"code\":\"tr\""));
        assertTrue(payload.contains("Ayşe \\\"Test\\\""));
        new ObjectMapper().readTree(payload);
    }

    @Test
    void buildsCustomerFriendlyCompletionPaymentStatuses() {
        assertEquals("3.500,00 TL nakit tahsil edildi",
                service.buildPaymentStatus(PaymentMethod.CASH, new BigDecimal("3500"), BigDecimal.ZERO));
        assertEquals("300,00 TL kartla tahsil edildi; 200,00 TL cari hesaba aktarıldı",
                service.buildPaymentStatus(PaymentMethod.CREDIT_CARD,
                        new BigDecimal("300"), new BigDecimal("200")));
        assertEquals("500,00 TL cari hesaba aktarıldı; tahsilat alınmadı",
                service.buildPaymentStatus(PaymentMethod.CURRENT_ACCOUNT,
                        BigDecimal.ZERO, new BigDecimal("500")));
        assertEquals("Garanti kapsamında; tahsilat alınmadı",
                service.buildPaymentStatus(PaymentMethod.WARRANTY, BigDecimal.ZERO, BigDecimal.ZERO));
        assertEquals("Ücretsiz işlem; tahsilat alınmadı",
                service.buildPaymentStatus(PaymentMethod.CASH, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    void doesNotResolveCustomerForCompanyOutsidePilot() {
        ServiceTicket ticket = ServiceTicket.builder()
                .id(100L)
                .companyId(99L)
                .customerId(20L)
                .status(ServiceTicket.TicketStatus.PENDING)
                .build();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));

        service.notifyServiceCreated(100L);

        verify(customerRepository, never()).findById(20L);
    }

    @Test
    void customerWithoutExplicitConsentIsNeverQueued() {
        WhatsAppNotificationService persistentService = persistentService();
        ServiceTicket ticket = eligibleTicket();
        Customer customer = eligibleCustomer();
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));

        persistentService.notifyServiceCreated(100L);

        verify(outboxRepository, never()).enqueueIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void explicitlyConsentedCustomerIsQueuedIdempotently() {
        WhatsAppNotificationService persistentService = persistentService();
        ServiceTicket ticket = eligibleTicket();
        Customer customer = eligibleCustomer();
        customer.setWhatsappOptIn(true);
        customer.setWhatsappOptInAt(LocalDateTime.now().minusMinutes(1));
        customer.setWhatsappOptInSource(WhatsAppConsentSource.VERBAL_CONFIRMATION);
        when(ticketRepository.findById(100L)).thenReturn(Optional.of(ticket));
        when(customerRepository.findById(20L)).thenReturn(Optional.of(customer));
        when(outboxRepository.enqueueIfAbsent(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1);

        persistentService.notifyServiceCreated(100L);

        verify(outboxRepository).enqueueIfAbsent(eq(10L), eq(100L), eq("SERVICE_CREATED"),
                eq("service-created:10:100"), eq("+905551112233"),
                eq("pusula_service_created"), eq("tr"), anyString(), any(LocalDateTime.class));
    }

    private WhatsAppNotificationService persistentService() {
        WhatsAppNotificationService value = new WhatsAppNotificationService(
                customerRepository, ticketRepository, outboxRepository, new ObjectMapper());
        ReflectionTestUtils.setField(value, "allowedCompanyIds", "10");
        ReflectionTestUtils.setField(value, "apiEnabled", true);
        ReflectionTestUtils.setField(value, "provider", "META");
        ReflectionTestUtils.setField(value, "templateLanguage", "tr");
        ReflectionTestUtils.setField(value, "serviceCreatedTemplate", "pusula_service_created");
        return value;
    }

    private ServiceTicket eligibleTicket() {
        return ServiceTicket.builder().id(100L).companyId(10L).customerId(20L)
                .description("Klima arızası").status(ServiceTicket.TicketStatus.PENDING).build();
    }

    private Customer eligibleCustomer() {
        return Customer.builder().id(20L).companyId(10L).name("Ayşe")
                .phone("0555 111 22 33").build();
    }
}
