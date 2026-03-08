package com.wearhouse.order.support.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.current.CurrentUserPrincipal;
import com.wearhouse.common.security.passport.PassportHeaders;
import com.wearhouse.common.security.passport.PassportSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
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
        String encodedUser = request.getHeader(PassportHeaders.USER);
        String signature = request.getHeader(PassportHeaders.SIGNATURE);
        String timestamp = request.getHeader(PassportHeaders.TIMESTAMP);

        if (encodedUser == null || signature == null || timestamp == null) {
            writeUnauthorized(response, "PASSPORT_MISSING", "passport 헤더가 없습니다.");
            return;
        }
        if (!passportSigner.verify(encodedUser, timestamp, signature)) {
            writeUnauthorized(response, "PASSPORT_INVALID", "passport 서명이 유효하지 않습니다.");
            return;
        }

        OrderPassportPayload payload;
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encodedUser);
            String json = new String(bytes, StandardCharsets.UTF_8);
            payload = objectMapper.readValue(json, OrderPassportPayload.class);
        } catch (Exception exception) {
            writeUnauthorized(response, "PASSPORT_INVALID", "passport payload가 유효하지 않습니다.");
            return;
        }

        CurrentUserPrincipal principal = new CurrentUserPrincipal(
                payload.userId(),
                payload.userType(),
                payload.roles(),
                payload.userVersion()
        );
        List<SimpleGrantedAuthority> authorities = payload.roles() == null
                ? List.of()
                : payload.roles().stream().map(SimpleGrantedAuthority::new).toList();

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, authorities));
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}
