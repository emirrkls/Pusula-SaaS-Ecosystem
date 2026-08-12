package com.pusula.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthFilter;
    private final AuthenticationProvider authenticationProvider;
    private final UploadSecurityFilter uploadSecurityFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthFilter, AuthenticationProvider authenticationProvider,
            UploadSecurityFilter uploadSecurityFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.authenticationProvider = authenticationProvider;
        this.uploadSecurityFilter = uploadSecurityFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/authenticate", "/api/auth/register-individual", "/api/auth/google",
                                "/api/public/**", "/api/subscription/plans", "/api/payment/webhook/**",
                                "/h2-console/**", "/uploads/**", "/downloads/**").permitAll()
                        .requestMatchers("/api/auth/register").hasAnyRole("COMPANY_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/finance/**").hasAnyRole("COMPANY_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/settings/**").hasAnyRole("COMPANY_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/push-devices/**").hasAnyRole("COMPANY_ADMIN", "SUPER_ADMIN", "TECHNICIAN")
                        .requestMatchers("/api/tickets/*/assign").hasAnyRole("COMPANY_ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> headers.frameOptions(frame -> frame.disable())) // Allow H2 Console in iframe
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(uploadSecurityFilter, JwtAuthenticationFilter.class);

        return http.build();
    }
}
