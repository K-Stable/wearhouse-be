package com.wearhouse.apigateway.config;

import com.wearhouse.common.security.passport.PassportHeaders;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class ApiGatewayCorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;
    private final String[] allowedOriginPatterns;

    public ApiGatewayCorsConfig(
            @Value("${wearhouse.cors.allowed-origins:}") String allowedOriginsProperty,
            @Value("${wearhouse.cors.allowed-origin-patterns:}") String allowedOriginPatternsProperty
    ) {
        this.allowedOrigins = splitCsv(allowedOriginsProperty);
        this.allowedOriginPatterns = splitCsv(allowedOriginPatternsProperty);
    }

    private String[] splitCsv(String rawValue) {
        return Arrays.stream(rawValue.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        var registration = registry.addMapping("/**");
        if (allowedOrigins.length > 0) {
            registration.allowedOrigins(allowedOrigins);
        }
        if (allowedOriginPatterns.length > 0) {
            registration.allowedOriginPatterns(allowedOriginPatterns);
        }
        registration
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders(HttpHeaders.AUTHORIZATION, PassportHeaders.VERIFIED)
                .allowCredentials(true)
                .maxAge(3600);
    }
}
