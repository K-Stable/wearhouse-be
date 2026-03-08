package com.wearhouse.product.support.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class ProductSecurityConfig {

    private final ProductPassportAuthenticationFilter productPassportAuthenticationFilter;

    public ProductSecurityConfig(ProductPassportAuthenticationFilter productPassportAuthenticationFilter) {
        this.productPassportAuthenticationFilter = productPassportAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain productFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/internal/**", "/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/buyer/products/**").permitAll()
                        .requestMatchers("/api/v1/seller/products/**").hasRole("SELLER")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(productPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
