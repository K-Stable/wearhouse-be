package com.wearhouse.apigateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.apigateway.security.dto.PassportContext;
import com.wearhouse.common.security.passport.PassportHeaders;
import com.wearhouse.common.security.passport.PassportSigner;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Base64;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class GatewayPassportAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> SKIP_PREFIXES = Set.of(
            "/auth-service/api/v1/auth/",
            "/actuator",
            "/error"
    );
    private static final List<String> PUBLIC_SIGNUP_PATHS = List.of(
            "/user-service/api/v1/users/buyers/signup",
            "/user-service/api/v1/users/sellers/signup"
    );

    private final GatewayPassportService gatewayPassportService;
    private final ObjectMapper objectMapper;
    private final PassportSigner passportSigner;
    private final String buyerAccessCookieName;
    private final String sellerAccessCookieName;

    public GatewayPassportAuthenticationFilter(
            GatewayPassportService gatewayPassportService,
            ObjectMapper objectMapper,
            @Value("${wearhouse.gateway.passport.shared-secret}") String passportSharedSecret,
            @Value("${wearhouse.gateway.cookie.buyer-access-name:buyer_access_token}") String buyerAccessCookieName,
            @Value("${wearhouse.gateway.cookie.seller-access-name:seller_access_token}") String sellerAccessCookieName
    ) {
        this.gatewayPassportService = gatewayPassportService;
        this.objectMapper = objectMapper;
        this.passportSigner = new PassportSigner(passportSharedSecret);
        this.buyerAccessCookieName = buyerAccessCookieName;
        this.sellerAccessCookieName = sellerAccessCookieName;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return SKIP_PREFIXES.stream().anyMatch(path::startsWith)
                || PUBLIC_SIGNUP_PATHS.stream().anyMatch(path::equals);
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

        String accessToken = resolveAccessToken(request);
        if (accessToken == null || accessToken.isBlank()) {
            writeUnauthorized(response, "ACCESS_TOKEN_MISSING", "access token 이 없습니다.");
            return;
        }

        PassportContext passportContext;
        try {
            passportContext = gatewayPassportService.resolve(accessToken);
        } catch (Exception exception) {
            writeUnauthorized(response, "ACCESS_TOKEN_INVALID", "access token 검증에 실패했습니다.");
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

    private String resolveAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            return authorization.substring("Bearer ".length()).trim();
        }
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (buyerAccessCookieName.equals(cookie.getName()) || sellerAccessCookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String encodePassport(PassportContext passportContext) {
        try {
            String json = objectMapper.writeValueAsString(passportContext);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Passport 직렬화에 실패했습니다.", exception);
        }
    }

    private void writeUnauthorized(HttpServletResponse response, String code, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(
                "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}"
        );
    }
}
