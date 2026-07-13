package com.pusula.backend.controller;

import com.pusula.backend.entity.User;
import com.pusula.backend.service.PushDeviceService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PushDeviceController.class)
@ContextConfiguration(classes = {
        PushDeviceController.class,
        PushDeviceControllerSecurityTest.TestSecurityConfig.class
})
class PushDeviceControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockBean PushDeviceService service;

    @Test
    void anonymousRequestIsRejected() throws Exception {
        mvc.perform(post("/api/push-devices/register")
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isForbidden());
        verify(service, never()).register(any());
    }

    @Test
    void allSupportedRolesMayRegister() throws Exception {
        for (String role : new String[]{"COMPANY_ADMIN", "SUPER_ADMIN", "TECHNICIAN"}) {
            mvc.perform(post("/api/push-devices/register").with(user(role))
                            .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                    .andExpect(status().isNoContent());
        }
    }

    @Test
    void unsupportedRoleIsRejected() throws Exception {
        mvc.perform(post("/api/push-devices/register").with(user("SUPER_ADMIN_READONLY"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isForbidden());
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

    private String validJson() {
        return "{\"token\":\"" + "a1".repeat(32) + "\",\"platform\":\"IOS\","
                + "\"environment\":\"SANDBOX\",\"bundleId\":\"com.pusula.service\"}";
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
