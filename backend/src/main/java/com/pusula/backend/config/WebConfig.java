package com.pusula.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.config.annotation.ContentNegotiationConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Consolidated Web MVC Configuration
 * 
 * This class consolidates all WebMvcConfigurer implementations to avoid
 * conflicts in Spring Boot 3.x handler mapping precedence.
 * 
 * CRITICAL: Having multiple WebMvcConfigurer beans can cause unpredictable
 * handler mapping order, leading to static resource handlers being checked
 * before controller mappings.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        // CRITICAL: Use AntPathMatcher for Spring Boot 2.x compatibility
        // PathPatternParser (Boot 3.x default) has different matching behavior
        configurer.setPatternParser(null); // Disables PathPatternParser, uses AntPathMatcher
        configurer.setUseTrailingSlashMatch(false);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Set resource handlers to LOWEST precedence to ensure controllers are checked
        // first
        registry.setOrder(Ordered.LOWEST_PRECEDENCE);

        // Only handle /uploads/** for uploaded files
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/")
                .resourceChain(false);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // CORS configuration consolidated from CorsConfig
        registry.addMapping("/api/**")
                .allowedOrigins("*") // Allow all origins for desktop app
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    @Override
    public void configureContentNegotiation(ContentNegotiationConfigurer configurer) {
        // Explicitly configure content negotiation to prevent conflicts
        configurer
                .defaultContentType(MediaType.APPLICATION_JSON)
                .favorParameter(false);
    }
}
