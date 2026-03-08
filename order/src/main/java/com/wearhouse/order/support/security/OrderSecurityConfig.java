package com.wearhouse.order.support.security;

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
public class OrderSecurityConfig {

    private final OrderPassportAuthenticationFilter orderPassportAuthenticationFilter;

    public OrderSecurityConfig(OrderPassportAuthenticationFilter orderPassportAuthenticationFilter) {
        this.orderPassportAuthenticationFilter = orderPassportAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain orderFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/internal/**", "/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/orders/**").hasRole("BUYER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/orders/**").hasRole("BUYER")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(orderPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
