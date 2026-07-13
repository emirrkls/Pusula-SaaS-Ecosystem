package com.pusula.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pusula.backend.entity.User;
import com.pusula.backend.entity.Vehicle;
import com.pusula.backend.repository.VehicleRepository;
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

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
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

@WebMvcTest(VehicleController.class)
@ContextConfiguration(classes = {
        VehicleController.class,
        VehicleControllerTenantSecurityTest.TestSecurityConfig.class
})
class VehicleControllerTenantSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private VehicleRepository vehicleRepository;

    @Test
    void companyAdminCanReadUpdateAndDeleteOwnVehicle() throws Exception {
        Vehicle vehicle = vehicle(1L, 10L, "09 ABC 123", "Ali", true);
        when(vehicleRepository.findByIdAndCompanyId(1L, 10L)).thenReturn(Optional.of(vehicle));
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(get("/api/vehicles/1").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(10));

        Vehicle update = vehicle(999L, 20L, "09 NEW 999", "Veli", false);
        mockMvc.perform(put("/api/vehicles/1")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.companyId").value(10))
                .andExpect(jsonPath("$.licensePlate").value("09 NEW 999"));

        mockMvc.perform(delete("/api/vehicles/1").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isOk());

        verify(vehicleRepository).delete(vehicle);
    }

    @Test
    void foreignTenantGetPutAndDeleteReturn404WithoutMutation() throws Exception {
        when(vehicleRepository.findByIdAndCompanyId(77L, 10L)).thenReturn(Optional.empty());
        Vehicle update = vehicle(77L, 20L, "34 FOREIGN", "Foreign", false);

        mockMvc.perform(get("/api/vehicles/77").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/vehicles/77")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(update)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/vehicles/77").with(user(10L, "COMPANY_ADMIN")))
                .andExpect(status().isNotFound());

        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verify(vehicleRepository, never()).delete(any(Vehicle.class));
    }

    @Test
    void technicianCannotCreateUpdateOrDeleteVehicles() throws Exception {
        Vehicle body = vehicle(null, 10L, "09 TECH", "Tech", true);
        String json = objectMapper.writeValueAsString(body);

        mockMvc.perform(post("/api/vehicles")
                        .with(user(10L, "TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/vehicles/1")
                        .with(user(10L, "TECHNICIAN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/vehicles/1").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isForbidden());

        verify(vehicleRepository, never()).save(any(Vehicle.class));
        verify(vehicleRepository, never()).delete(any(Vehicle.class));
    }

    @Test
    void createAlwaysUsesAuthenticatedCompanyId() throws Exception {
        Vehicle request = vehicle(999L, 20L, "09 SAFE 01", "Admin", true);
        when(vehicleRepository.save(any(Vehicle.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/vehicles")
                        .with(user(10L, "COMPANY_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(10));

        verify(vehicleRepository).save(any(Vehicle.class));
    }

    @Test
    void listAndActiveAreScopedToAuthenticatedCompany() throws Exception {
        Vehicle active = vehicle(1L, 10L, "09 ACTIVE", "Ali", true);
        Vehicle inactive = vehicle(2L, 10L, "09 PASSIVE", "Veli", false);
        when(vehicleRepository.findByCompanyId(10L)).thenReturn(List.of(active, inactive));
        when(vehicleRepository.findByCompanyIdAndIsActiveTrue(10L)).thenReturn(List.of(active));

        mockMvc.perform(get("/api/vehicles").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].companyId").value(10))
                .andExpect(jsonPath("$[1].companyId").value(10));

        mockMvc.perform(get("/api/vehicles/active").with(user(10L, "TECHNICIAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].companyId").value(10));

        verify(vehicleRepository).findByCompanyId(10L);
        verify(vehicleRepository).findByCompanyIdAndIsActiveTrue(10L);
    }

    private RequestPostProcessor user(
            Long companyId,
            String role) {
        User principal = new User();
        principal.setCompanyId(companyId);
        principal.setRole(role);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                principal.getAuthorities());
        return authentication(authentication);
    }

    private Vehicle vehicle(
            Long id,
            Long companyId,
            String licensePlate,
            String driverName,
            boolean active) {
        return Vehicle.builder()
                .id(id)
                .companyId(companyId)
                .licensePlate(licensePlate)
                .driverName(driverName)
                .isActive(active)
                .build();
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
