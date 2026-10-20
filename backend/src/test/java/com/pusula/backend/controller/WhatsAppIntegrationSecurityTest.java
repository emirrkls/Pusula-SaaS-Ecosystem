package com.pusula.backend.controller;

import com.pusula.backend.entity.User;
import com.pusula.backend.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringJUnitConfig(WhatsAppIntegrationSecurityTest.Config.class)
class WhatsAppIntegrationSecurityTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean WhatsAppOnboardingService service() { return mock(WhatsAppOnboardingService.class); }
        @Bean WhatsAppCoexistenceMirrorService mirror() { return mock(WhatsAppCoexistenceMirrorService.class); }
        @Bean WhatsAppIntegrationController controller(WhatsAppOnboardingService service, WhatsAppCoexistenceMirrorService mirror) {
            return new WhatsAppIntegrationController(service, mirror);
        }
    }
    @Autowired WhatsAppIntegrationController controller;
    @Autowired WhatsAppCoexistenceMirrorService mirror;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); reset(mirror); }
    void login(String role) {
        User user = new User(); user.setCompanyId(10L); user.setRole(role); user.setUsername("synthetic-test");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
    }
    @Test void technicianCannotReadContactsOrMessages() {
        login("TECHNICIAN");
        assertThrows(AccessDeniedException.class, () -> controller.messages(null,0));
        assertThrows(AccessDeniedException.class, () -> controller.contacts(0));
        verifyNoInteractions(mirror);
    }
    @Test void administratorReadsOnlyTheirOwnCompanyWithoutClientSuppliedTenantId() {
        login("COMPANY_ADMIN");
        controller.messages(null,0); controller.contacts(0);
        verify(mirror).read(10L,"MESSAGE",null,0);
        verify(mirror).read(10L,"CONTACT",null,0);
    }
}
