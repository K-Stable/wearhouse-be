package com.wearhouse.inventory.support.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class InventorySecurityConfig {

    private final InventoryPassportAuthenticationFilter inventoryPassportAuthenticationFilter;

    @Bean
    SecurityFilterChain inventoryFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/seller/inventories/**").hasRole("SELLER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/inventory/stocks").hasRole("SELLER")
                        .requestMatchers(HttpMethod.GET, "/api/v1/internal/inventory/stocks/**").hasRole("SELLER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/internal/inventory/stocks/availability/check")
                        .hasAnyRole("BUYER", "SELLER")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(inventoryPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
