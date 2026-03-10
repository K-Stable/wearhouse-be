package com.wearhouse.apigateway.config;

import com.wearhouse.apigateway.security.GatewayPassportAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class ApiGatewaySecurityConfig {

    private static final String[] ALLOWED_URL_PATTERNS = {
            "/",
            "/api/v1/**",
            "/actuator/**",
            "/error"
    };

    private final GatewayPassportAuthenticationFilter gatewayPassportAuthenticationFilter;

    @Bean
    SecurityFilterChain gatewayFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(ALLOWED_URL_PATTERNS).permitAll()
                        .anyRequest().denyAll()
                )
                .addFilterBefore(gatewayPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
