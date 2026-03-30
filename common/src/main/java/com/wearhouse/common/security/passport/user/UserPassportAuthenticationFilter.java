package com.wearhouse.common.security.passport.user;

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
@ConditionalOnProperty(name = "spring.application.name", havingValue = "user-service")
public class UserPassportAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final PassportSigner passportSigner;

    public UserPassportAuthenticationFilter(
            ObjectMapper objectMapper,
            @Value("${wearhouse.user.passport.shared-secret:${PASSPORT_SHARED_SECRET:wearhouse-passport-shared-secret}}")
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
        return path.startsWith("/api/v1/internal/")
                || isPublicUserPath(request.getMethod(), path)
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

    private boolean isPublicUserPath(String method, String path) {
        if ("GET".equalsIgnoreCase(method)) {
            return "/api/v1/users/buyers/login-id/availability".equals(path)
                    || "/api/v1/users/sellers/login-id/availability".equals(path);
        }
        if (!"POST".equalsIgnoreCase(method)) {
            return false;
        }
        return "/api/v1/users/buyers/signup".equals(path)
                || "/api/v1/users/buyers/email-code/send".equals(path)
                || "/api/v1/users/buyers/email-code/verify".equals(path)
                || "/api/v1/users/sellers/signup".equals(path)
                || "/api/v1/users/sellers/email-code/send".equals(path)
                || "/api/v1/users/sellers/email-code/verify".equals(path);
    }
}
