package com.wearhouse.apigateway.config;

import com.wearhouse.common.security.passport.gateway.GatewayPassportAuthenticationFilter;
import com.wearhouse.common.security.passport.gateway.GatewayJwtValidationFilter;
import com.wearhouse.common.security.passport.gateway.GatewayRequestPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ApiGatewaySecurityConfig {

    private final GatewayJwtValidationFilter gatewayJwtValidationFilter;
    private final GatewayPassportAuthenticationFilter gatewayPassportAuthenticationFilter;

    @Bean
    SecurityFilterChain gatewayFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(GatewayRequestPolicy.PUBLIC_URL_PATTERNS).permitAll()
                        .requestMatchers(GatewayRequestPolicy.BUYER_MYPAGE_PATTERN).authenticated()
                        .requestMatchers(GatewayRequestPolicy.BUYER_PATTERN).permitAll()
                        .requestMatchers(GatewayRequestPolicy.SELLER_PATTERN).hasRole("SELLER")
                        .anyRequest().authenticated()
                )
                .addFilterBefore(gatewayPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(gatewayJwtValidationFilter, GatewayPassportAuthenticationFilter.class);

        return http.build();
    }
}
