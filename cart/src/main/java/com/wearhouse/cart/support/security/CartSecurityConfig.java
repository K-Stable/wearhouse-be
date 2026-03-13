package com.wearhouse.cart.support.security;

import com.wearhouse.common.security.passport.cart.CartPassportAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class CartSecurityConfig {

    private final CartPassportAuthenticationFilter cartPassportAuthenticationFilter;

    @Bean
    SecurityFilterChain cartFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/buyer/carts/**").hasRole("BUYER")
                        .requestMatchers(HttpMethod.POST, "/api/v1/buyer/carts/**").hasRole("BUYER")
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/buyer/carts/**").hasRole("BUYER")
                        .requestMatchers(HttpMethod.DELETE, "/api/v1/buyer/carts/**").hasRole("BUYER")
                        .anyRequest().denyAll()
                )
                .addFilterBefore(cartPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
