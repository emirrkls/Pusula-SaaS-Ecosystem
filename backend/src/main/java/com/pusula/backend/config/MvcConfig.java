package com.pusula.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * MVC Configuration for Resource Handler Priority
 * 
 * This configuration ensures REST controllers are matched before
 * static resource handlers by properly configuring resource paths.
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {

    /**
     * Explicitly configure resource handlers for specific paths only.
     * API paths (/api/**) are NOT included, ensuring controllers take priority.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Only handle explicitly defined resource paths
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/");

        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/");

        // Do NOT add a /** catchall handler
    }

    /**
     * Force AntPathMatcher usage for backwards compatibility
     */
    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.setPatternParser(null); // Disable PathPatternParser
    }
}
