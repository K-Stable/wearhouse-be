package com.wearhouse.common.security.passport.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.passport.PassportHeaders;
import com.wearhouse.common.security.passport.PassportSigner;
import com.wearhouse.common.security.passport.gateway.dto.PassportContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayPassportAuthenticationFilter extends OncePerRequestFilter {

    private final ObjectMapper objectMapper;
    private final PassportSigner passportSigner;

    public GatewayPassportAuthenticationFilter(
            ObjectMapper objectMapper,
            @Value("${wearhouse.gateway.passport.shared-secret}") String passportSharedSecret
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
        return GatewayRequestPolicy.isFrameworkPath(path) || GatewayRequestPolicy.isAuthOrSignupPublicPath(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        MutableHeaderHttpServletRequest wrapped = new MutableHeaderHttpServletRequest(request);
        wrapped.removeHeader(PassportHeaders.USER);
        wrapped.removeHeader(PassportHeaders.SIGNATURE);
        wrapped.removeHeader(PassportHeaders.TIMESTAMP);

        String path = request.getRequestURI();
        if (GatewayRequestPolicy.isPublicBuyerPath(path)) {
            filterChain.doFilter(wrapped, response);
            return;
        }

        Object contextAttribute = request.getAttribute(GatewayJwtValidationFilter.PASSPORT_CONTEXT_ATTRIBUTE);
        if (!(contextAttribute instanceof PassportContext passportContext)) {
            filterChain.doFilter(wrapped, response);
            return;
        }

        String encodedUser = encodePassport(passportContext);
        String timestamp = String.valueOf(Instant.now().toEpochMilli());
        String signature = passportSigner.sign(encodedUser, timestamp);

        wrapped.putHeader(PassportHeaders.USER, encodedUser);
        wrapped.putHeader(PassportHeaders.TIMESTAMP, timestamp);
        wrapped.putHeader(PassportHeaders.SIGNATURE, signature);

        filterChain.doFilter(wrapped, response);
    }

    private String encodePassport(PassportContext passportContext) {
        try {
            String json = objectMapper.writeValueAsString(passportContext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Passport 직렬화에 실패했습니다.", exception);
        }
    }

}
