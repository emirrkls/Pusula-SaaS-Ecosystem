package com.pusula.backend.controller;

import com.pusula.backend.dto.BusinessAssetDTO;
import com.pusula.backend.entity.User;
import com.pusula.backend.service.BusinessAssetService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(BusinessAssetController.class)
@ContextConfiguration(classes = { BusinessAssetController.class,
        BusinessAssetControllerTest.TestSecurityConfig.class })
class BusinessAssetControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockBean private BusinessAssetService service;

    @Test
    void technicianCannotAccessBusinessAssets() throws Exception {
        mockMvc.perform(get("/api/business-assets").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/business-assets").with(user(10L, "TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isForbidden());
        verify(service, never()).getAll();
        verify(service, never()).create(any());
    }

    @Test
    void companyAdminCanListCreateUpdateAndDelete() throws Exception {
        BusinessAssetDTO dto = asset(1L);
        when(service.getAll()).thenReturn(List.of(dto));
        when(service.create(any())).thenReturn(dto);
        when(service.update(eq(1L), any())).thenReturn(Optional.of(dto));
        when(service.delete(1L)).thenReturn(true);

        mockMvc.perform(get("/api/business-assets").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].assetName").value("Vakum Pompası"));
        mockMvc.perform(post("/api/business-assets").with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));
        mockMvc.perform(put("/api/business-assets/1").with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(validJson()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/business-assets/1").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void invalidValuesReturnBadRequest() throws Exception {
        List<String> invalid = List.of(
                "{\"assetName\":\" \",\"quantity\":1,\"condition\":\"ACTIVE\"}",
                "{\"assetName\":\"Matkap\",\"quantity\":0,\"condition\":\"ACTIVE\"}",
                "{\"assetName\":\"Matkap\",\"quantity\":1,\"condition\":\"UNKNOWN\"}",
                "{\"assetName\":\"Matkap\",\"quantity\":1,\"condition\":\"ACTIVE\",\"purchasePrice\":-1}"
        );
        for (String body : invalid) {
            mockMvc.perform(post("/api/business-assets").with(user(10L, "COMPANY_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(service, never()).create(any());
    }

    private static BusinessAssetDTO asset(Long id) {
        BusinessAssetDTO dto = new BusinessAssetDTO();
        dto.setId(id);
        dto.setAssetName("Vakum Pompası");
        dto.setQuantity(1);
        dto.setCondition("ACTIVE");
        dto.setPurchasePrice(new BigDecimal("12500.00"));
        return dto;
    }

    private static String validJson() {
        return "{\"assetName\":\"Vakum Pompası\",\"category\":\"Takım\",\"quantity\":1,"
                + "\"condition\":\"ACTIVE\",\"purchasePrice\":12500}";
    }

    private static RequestPostProcessor user(Long companyId, String role) {
        User principal = new User();
        principal.setCompanyId(companyId);
        principal.setRole(role);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());
        return authentication(authentication);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated()).build();
        }
    }
}
