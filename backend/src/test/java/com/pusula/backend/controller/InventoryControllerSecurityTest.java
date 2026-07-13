package com.pusula.backend.controller;

import com.pusula.backend.dto.InventoryDTO;
import com.pusula.backend.entity.Inventory;
import com.pusula.backend.entity.User;
import com.pusula.backend.repository.InventoryRepository;
import com.pusula.backend.service.InventoryService;
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

@WebMvcTest(InventoryController.class)
@ContextConfiguration(classes = {
        InventoryController.class,
        InventoryControllerSecurityTest.TestSecurityConfig.class
})
class InventoryControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private InventoryService inventoryService;

    @MockBean
    private InventoryRepository inventoryRepository;

    @Test
    void technicianCannotCreateUpdateOrDeleteInventory() throws Exception {
        mockMvc.perform(post("/api/inventory")
                        .with(user(10L, "TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInventoryJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/inventory/1")
                        .with(user(10L, "TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInventoryJson()))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/inventory/1").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isForbidden());

        verify(inventoryService, never()).createInventory(any());
        verify(inventoryService, never()).updateInventory(any(), any());
        verify(inventoryService, never()).deleteInventory(any());
    }

    @Test
    void companyAdminCanCreateUpdateAndDeleteOwnInventory() throws Exception {
        InventoryDTO response = inventoryDto(1L, "Filter", 5);
        when(inventoryService.createInventory(any(InventoryDTO.class))).thenReturn(response);
        when(inventoryService.updateInventory(eq(1L), any(InventoryDTO.class))).thenReturn(Optional.of(response));
        when(inventoryService.deleteInventory(1L)).thenReturn(true);

        mockMvc.perform(post("/api/inventory")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInventoryJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));

        mockMvc.perform(put("/api/inventory/1")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInventoryJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.partName").value("Filter"));

        mockMvc.perform(delete("/api/inventory/1").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isNoContent());
    }

    @Test
    void foreignTenantUpdateAndDeleteReturn404() throws Exception {
        when(inventoryService.updateInventory(eq(77L), any(InventoryDTO.class))).thenReturn(Optional.empty());
        when(inventoryService.deleteInventory(77L)).thenReturn(false);

        mockMvc.perform(put("/api/inventory/77")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validInventoryJson()))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/inventory/77").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidInventoryValuesReturn400() throws Exception {
        List<String> invalidBodies = List.of(
                "{\"partName\":\"  \",\"quantity\":1,\"buyPrice\":1,\"sellPrice\":1,\"criticalLevel\":0}",
                "{\"partName\":\"Filter\",\"quantity\":-1,\"buyPrice\":1,\"sellPrice\":1,\"criticalLevel\":0}",
                "{\"partName\":\"Filter\",\"quantity\":1,\"buyPrice\":-1,\"sellPrice\":1,\"criticalLevel\":0}",
                "{\"partName\":\"Filter\",\"quantity\":1,\"buyPrice\":1,\"sellPrice\":-1,\"criticalLevel\":0}",
                "{\"partName\":\"Filter\",\"quantity\":1,\"buyPrice\":1,\"sellPrice\":1,\"criticalLevel\":-1}"
        );

        for (String body : invalidBodies) {
            mockMvc.perform(post("/api/inventory")
                            .with(user(10L, "COMPANY_ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(put("/api/inventory/1")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBodies.get(1)))
                .andExpect(status().isBadRequest());

        verify(inventoryService, never()).createInventory(any());
        verify(inventoryService, never()).updateInventory(any(), any());
    }

    @Test
    void technicianGetDoesNotExposeBuyPrice() throws Exception {
        Inventory inventory = Inventory.builder()
                .id(1L)
                .companyId(10L)
                .partName("Filter")
                .quantity(5)
                .buyPrice(new BigDecimal("10.00"))
                .sellPrice(new BigDecimal("20.00"))
                .criticalLevel(1)
                .build();
        inventory.setBarcode("12345");
        when(inventoryRepository.findByCompanyId(10L)).thenReturn(List.of(inventory));
        when(inventoryRepository.findByBarcodeNormalized("12345", 10L)).thenReturn(Optional.of(inventory));

        mockMvc.perform(get("/api/inventory").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sellPrice").value(20.00))
                .andExpect(jsonPath("$[0].buyPrice").doesNotExist());

        mockMvc.perform(get("/api/inventory/barcode/12345").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellPrice").value(20.00))
                .andExpect(jsonPath("$.buyPrice").doesNotExist());
    }

    private InventoryDTO inventoryDto(Long id, String partName, int quantity) {
        return InventoryDTO.builder()
                .id(id)
                .partName(partName)
                .quantity(quantity)
                .buyPrice(new BigDecimal("10.00"))
                .sellPrice(new BigDecimal("20.00"))
                .criticalLevel(1)
                .build();
    }

    private String validInventoryJson() {
        return "{\"companyId\":999999,\"partName\":\"Filter\",\"quantity\":5,"
                + "\"buyPrice\":10,\"sellPrice\":20,\"criticalLevel\":1}";
    }

    private RequestPostProcessor user(Long companyId, String role) {
        User principal = new User();
        principal.setCompanyId(companyId);
        principal.setRole(role);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        return authentication(authentication);
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .build();
        }
    }
}
