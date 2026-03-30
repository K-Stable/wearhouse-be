package com.wearhouse.common.security.passport.authentication;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.passport.PassportHeaders;
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
@ConditionalOnProperty(name = "spring.application.name", havingValue = "product-service")
public class ProductPassportAuthenticationFilter extends OncePerRequestFilter {

    private static final String SELLER_SEASON_LIST_PATH = "/api/v1/seller/products/seasons";

    private final ObjectMapper objectMapper;
    private final PassportSigner passportSigner;

    public ProductPassportAuthenticationFilter(
            ObjectMapper objectMapper,
            @Value("${wearhouse.product.passport.shared-secret:${PASSPORT_SHARED_SECRET:wearhouse-passport-shared-secret}}")
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
        if ("GET".equalsIgnoreCase(request.getMethod()) && path.startsWith("/api/v1/buyer/products")) {
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
        if (!ProductPassportAuthenticationSupport.authenticateRequest(objectMapper, passportSigner, request, response)) {
            return;
        }
        if (isSellerSeasonListRequest(request)) {
            response.setHeader(PassportHeaders.VERIFIED, "true");
        }

        filterChain.doFilter(request, response);
    }

    private boolean isSellerSeasonListRequest(HttpServletRequest request) {
        return "GET".equalsIgnoreCase(request.getMethod()) && SELLER_SEASON_LIST_PATH.equals(request.getRequestURI());
    }

}
