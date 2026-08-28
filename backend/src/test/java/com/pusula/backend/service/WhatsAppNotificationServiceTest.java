package com.pusula.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.ServiceTicket;
import com.pusula.backend.repository.CustomerRepository;
import com.pusula.backend.repository.ServiceTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WhatsAppNotificationServiceTest {

    @Mock CustomerRepository customerRepository;
    @Mock ServiceTicketRepository ticketRepository;

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
}
