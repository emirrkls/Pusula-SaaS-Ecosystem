package com.pusula.backend.controller;

import com.pusula.backend.entity.User;
import com.pusula.backend.service.GooglePlayBillingStatusService;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GooglePlayBillingStatusControllerTest {
    @Configuration @EnableMethodSecurity
    static class Config {
        @Bean GooglePlayBillingStatusService service() { return mock(GooglePlayBillingStatusService.class); }
        @Bean GooglePlayBillingStatusController controller(GooglePlayBillingStatusService service) {
            return new GooglePlayBillingStatusController(service);
        }
    }
    AnnotationConfigApplicationContext context;
    GooglePlayBillingStatusController controller;
    GooglePlayBillingStatusService service;
    @BeforeEach void setup() {
        context=new AnnotationConfigApplicationContext(Config.class);
        controller=context.getBean(GooglePlayBillingStatusController.class);
        service=context.getBean(GooglePlayBillingStatusService.class);
    }
    @AfterEach void close() { SecurityContextHolder.clearContext(); context.close(); }
    void login(String role,Long company) {
        User user=new User(); user.setRole(role); user.setCompanyId(company);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user,null,user.getAuthorities()));
    }
    @Test void administratorStatusUsesAuthenticatedTenantOnly() {
        login("COMPANY_ADMIN",10L); controller.status(); verify(service).status(10L);
    }
    @Test void technicianCannotReadBillingReadinessOrReceiptMetadata() {
        login("TECHNICIAN",10L); assertThrows(AccessDeniedException.class,()->controller.status()); verifyNoInteractions(service);
    }
    @Test void anonymousCannotReadBillingMetadata() {
        assertThrows(org.springframework.security.core.AuthenticationException.class,()->controller.status()); verifyNoInteractions(service);
    }
}
