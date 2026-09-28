package com.pusula.backend.controller;

import com.pusula.backend.dto.CustomerWhatsAppConsentDTO;
import com.pusula.backend.dto.UpdateCustomerWhatsAppConsentRequest;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.WhatsAppConsentSource;
import com.pusula.backend.service.CustomerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CustomerController.class)
@ContextConfiguration(classes = {
        CustomerController.class,
        CustomerWhatsAppConsentControllerTest.TestSecurityConfig.class
})
class CustomerWhatsAppConsentControllerTest {
    @Autowired MockMvc mvc;
    @MockBean CustomerService customerService;

    @Test
    void companyAdminMayRecordExplicitConsent() throws Exception {
        LocalDateTime optedInAt = LocalDateTime.of(2026, 9, 28, 12, 0);
        when(customerService.updateWhatsAppConsent(eq(25L), any(UpdateCustomerWhatsAppConsentRequest.class)))
                .thenReturn(new CustomerWhatsAppConsentDTO(
                        25L, true, optedInAt, WhatsAppConsentSource.WRITTEN_FORM, null));

        mvc.perform(put("/api/customers/25/whatsapp-consent")
                        .with(user("COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":true,\"source\":\"WRITTEN_FORM\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value(25))
                .andExpect(jsonPath("$.optedIn").value(true))
                .andExpect(jsonPath("$.source").value("WRITTEN_FORM"));
    }

    @Test
    void missingConsentStateIsRejectedBeforeServiceCall() throws Exception {
        mvc.perform(put("/api/customers/25/whatsapp-consent")
                        .with(user("COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"source\":\"WRITTEN_FORM\"}"))
                .andExpect(status().isBadRequest());

        verify(customerService, never()).updateWhatsAppConsent(eq(25L), any());
    }

    @Test
    void technicianCannotReadOrChangeConsent() throws Exception {
        mvc.perform(get("/api/customers/25/whatsapp-consent").with(user("TECHNICIAN")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/customers/25/whatsapp-consent")
                        .with(user("TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"optedIn\":false}"))
                .andExpect(status().isForbidden());

        verify(customerService, never()).getWhatsAppConsent(25L);
        verify(customerService, never()).updateWhatsAppConsent(eq(25L), any());
    }

    private RequestPostProcessor user(String role) {
        User principal = new User();
        principal.setId(1L);
        principal.setCompanyId(10L);
        principal.setRole(role);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(auth);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
