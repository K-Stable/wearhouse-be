package com.wearhouse.common.security.passport.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.passport.PassportAuthenticationSupport;
import com.wearhouse.common.security.passport.PassportSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@ConditionalOnProperty(name = "spring.application.name", havingValue = "order-service")
public class OrderPassportAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final PassportSigner passportSigner;

    public OrderPassportAuthenticationFilter(
            ObjectMapper objectMapper,
            @Value("${wearhouse.order.passport.shared-secret:${PASSPORT_SHARED_SECRET:wearhouse-passport-shared-secret}}")
            String passportSharedSecret
    ) {
        this.objectMapper = objectMapper;
        this.passportSigner = new PassportSigner(passportSharedSecret);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        if ("POST".equalsIgnoreCase(request.getMethod())
                && ("/api/v1/buyer/guest/orders".equals(path)
                || path.startsWith("/api/v1/buyer/guest/orders/"))) {
            return true;
        }
        return path.startsWith("/api/v1/internal/")
                || path.startsWith("/actuator")
                || path.startsWith("/error");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        if (!PassportAuthenticationSupport.authenticateRequest(objectMapper, passportSigner, request, response)) {
            return;
        }

        filterChain.doFilter(request, response);
    }
}
