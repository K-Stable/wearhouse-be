package com.wearhouse.order.support.security;

import com.wearhouse.common.security.passport.authentication.OrderPassportAuthenticationFilter;
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
public class OrderSecurityConfig {

    private final OrderPassportAuthenticationFilter orderPassportAuthenticationFilter;

    @Bean
    SecurityFilterChain orderFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/internal/**", "/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/buyer/guest/orders/**").permitAll()
                        .requestMatchers("/api/v1/buyer/guest/orders/**").denyAll()
                        .requestMatchers("/api/v1/buyer/orders/**").hasRole("BUYER")
                        .requestMatchers("/api/v1/seller/orders/**").hasRole("SELLER")
                        .requestMatchers("/api/v1/orders/**").hasRole("BUYER")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(orderPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
