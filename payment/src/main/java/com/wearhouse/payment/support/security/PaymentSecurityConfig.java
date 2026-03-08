package com.wearhouse.payment.support.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class PaymentSecurityConfig {

    private final PaymentPassportAuthenticationFilter paymentPassportAuthenticationFilter;

    public PaymentSecurityConfig(PaymentPassportAuthenticationFilter paymentPassportAuthenticationFilter) {
        this.paymentPassportAuthenticationFilter = paymentPassportAuthenticationFilter;
    }

    @Bean
    SecurityFilterChain paymentFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/internal/**", "/actuator/**", "/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(paymentPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
