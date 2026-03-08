package com.wearhouse.apigateway.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class GatewaySecurityConfig {

    private final GatewayPassportAuthenticationFilter gatewayPassportAuthenticationFilter;

    public GatewaySecurityConfig(GatewayPassportAuthenticationFilter gatewayPassportAuthenticationFilter) {
        this.gatewayPassportAuthenticationFilter = gatewayPassportAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain gatewayFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                .addFilterBefore(gatewayPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
