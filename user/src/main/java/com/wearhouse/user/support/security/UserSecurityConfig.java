package com.wearhouse.user.support.security;

import com.wearhouse.common.security.passport.authentication.UserPassportAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@RequiredArgsConstructor
public class UserSecurityConfig {

    private final UserPassportAuthenticationFilter userPassportAuthenticationFilter;

    @Bean
    SecurityFilterChain userFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/api/v1/internal/**", "/actuator/**", "/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/buyers/login-id/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/buyers/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/buyers/email-code/send").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/buyers/email-code/verify").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/sellers/login-id/availability").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/sellers/signup").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/sellers/email-code/send").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/sellers/email-code/verify").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(userPassportAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
