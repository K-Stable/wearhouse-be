package com.wearhouse.common.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "wearhouse.auth.jwt.secret")
public class JwtProvider {

    private static final String CLAIM_TOKEN_TYPE = "tokenType";
    private static final String CLAIM_SELLER = "seller";
    private static final String CLAIM_BUYER = "buyer";
    private static final String CLAIM_ID = "id";
    private static final String CLAIM_VERSION = "version";
    private static final String CLAIM_EMAIL = "email";
    private static final String SUBJECT_ACTOR = "wearhouse-actor";

    private final JwtProperties jwtProperties;

    public IssuedAccessToken issueAccessToken(Long userId, String userType, String email, Long userVersion) {
        IssuedToken issuedToken = issueToken(
                userId,
                userType,
                email,
                userVersion,
                JwtTokenType.ACCESS,
                jwtProperties.accessTokenMinutes() * 60L
        );
        return new IssuedAccessToken(
                issuedToken.value(),
                toLocalDateTime(issuedToken.issuedAt()),
                toLocalDateTime(issuedToken.expiresAt())
        );
    }

    public IssuedRefreshToken issueRefreshToken(Long userId, String userType, String email, Long userVersion) {
        IssuedToken issuedToken = issueToken(
                userId,
                userType,
                email,
                userVersion,
                JwtTokenType.REFRESH,
                jwtProperties.refreshTokenDays() * 24L * 60L * 60L
        );
        return new IssuedRefreshToken(
                issuedToken.value(),
                toLocalDateTime(issuedToken.issuedAt()),
                toLocalDateTime(issuedToken.expiresAt())
        );
    }

    public DecodedAccessToken decodeAccessToken(String accessToken) {
        Claims claims = parseClaims(accessToken, JwtTokenType.ACCESS);
        DecodedActor actor = decodeActor(claims);
        List<String> roles = List.of("ROLE_" + actor.userType().toUpperCase());

        return new DecodedAccessToken(
                actor.userId(),
                actor.userType(),
                roles,
                actor.userVersion(),
                toLocalDateTime(claims.getIssuedAt().toInstant()),
                toLocalDateTime(claims.getExpiration().toInstant())
        );
    }

    public DecodedRefreshToken decodeRefreshToken(String refreshToken) {
        Claims claims = parseClaims(refreshToken, JwtTokenType.REFRESH);
        DecodedActor actor = decodeActor(claims);
        return new DecodedRefreshToken(
                actor.userId(),
                actor.userType(),
                actor.userVersion(),
                toLocalDateTime(claims.getIssuedAt().toInstant()),
                toLocalDateTime(claims.getExpiration().toInstant())
        );
    }

    private DecodedActor decodeActor(Claims claims) {
        @SuppressWarnings("unchecked")
        Map<String, Object> seller = claims.get(CLAIM_SELLER, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> buyer = claims.get(CLAIM_BUYER, Map.class);

        String userType;
        Map<String, Object> actor;
        if (seller != null && !seller.isEmpty()) {
            userType = "SELLER";
            actor = seller;
        } else if (buyer != null && !buyer.isEmpty()) {
            userType = "BUYER";
            actor = buyer;
        } else {
            throw new IllegalStateException("seller/buyer 클레임이 비어 있습니다.");
        }

        Long userId = parseLong(actor.get(CLAIM_ID));
        Long userVersion = parseLong(actor.get(CLAIM_VERSION));
        return new DecodedActor(userId, userType, userVersion);
    }

    private Long parseLong(Object value) {
        if (value == null) {
            throw new IllegalStateException("숫자 클레임이 비어 있습니다.");
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("숫자 클레임 형식이 올바르지 않습니다.", exception);
        }
    }

    private SecretKey resolveSecretKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaims(String token, JwtTokenType expectedTokenType) {
        SecretKey secretKey = resolveSecretKey();
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(jwtProperties.issuer())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String tokenType = claims.get(CLAIM_TOKEN_TYPE, String.class);
        if (!expectedTokenType.name().equals(tokenType)) {
            throw new IllegalStateException("유효하지 않은 토큰 유형입니다.");
        }
        return claims;
    }

    private IssuedToken issueToken(
            Long userId,
            String userType,
            String email,
            Long userVersion,
            JwtTokenType tokenType,
            long ttlSeconds
    ) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ttlSeconds);
        SecretKey secretKey = resolveSecretKey();
        Map<String, Object> actor = buildActor(userId, userVersion, email);

        var builder = Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(SUBJECT_ACTOR)
                .claim(CLAIM_TOKEN_TYPE, tokenType.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt));
        if ("SELLER".equalsIgnoreCase(userType)) {
            builder.claim(CLAIM_SELLER, actor);
        } else {
            builder.claim(CLAIM_BUYER, actor);
        }

        return new IssuedToken(builder.signWith(secretKey).compact(), issuedAt, expiresAt);
    }

    private Map<String, Object> buildActor(Long userId, Long userVersion, String email) {
        Map<String, Object> actor = new HashMap<>();
        actor.put(CLAIM_ID, userId);
        actor.put(CLAIM_VERSION, userVersion);
        if (email != null && !email.isBlank()) {
            actor.put(CLAIM_EMAIL, email);
        }
        return actor;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    public String extractAccessToken(HttpServletRequest request) {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String trimmed = authorization.trim();
        if (!trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return null;
        }
        return trimmed.substring(7).trim();
    }

    public void applyTokens(
            HttpServletResponse response,
            String userType,
            String accessToken,
            String refreshToken
    ) {
        putAccessToken(response, accessToken);
        putRefreshToken(response, userType, refreshToken);
    }

    public String extractRefreshToken(HttpServletRequest request, String userType) {
        String cookieName = resolveRefreshCookieName(userType);
        if (request.getCookies() == null) {
            return null;
        }
        Optional<Cookie> cookie = Arrays.stream(request.getCookies())
                .filter(each -> cookieName.equals(each.getName()))
                .findFirst();
        return cookie.map(Cookie::getValue).orElse(null);
    }

    public void clearRefreshToken(HttpServletResponse response, String userType) {
        String cookieName = resolveRefreshCookieName(userType);
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite())
                .path(jwtProperties.cookiePath())
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void putAccessToken(HttpServletResponse response, String accessToken) {
        response.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        response.addHeader(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.AUTHORIZATION);
    }

    private void putRefreshToken(HttpServletResponse response, String userType, String refreshToken) {
        String cookieName = resolveRefreshCookieName(userType);
        ResponseCookie cookie = ResponseCookie.from(cookieName, refreshToken)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecure())
                .sameSite(jwtProperties.cookieSameSite())
                .path(jwtProperties.cookiePath())
                .maxAge(jwtProperties.refreshTokenDays() * 24L * 60L * 60L)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String resolveRefreshCookieName(String userType) {
        if ("SELLER".equalsIgnoreCase(userType)) {
            return jwtProperties.sellerRefreshCookieName();
        }
        if ("BUYER".equalsIgnoreCase(userType)) {
            return jwtProperties.buyerRefreshCookieName();
        }
        throw new IllegalArgumentException("지원하지 않는 userType 입니다: " + userType);
    }

    public record IssuedAccessToken(
            String accessToken,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
    }

    public record IssuedRefreshToken(
            String refreshToken,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
    }

    public record DecodedAccessToken(
            Long userId,
            String userType,
            List<String> roles,
            Long userVersion,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
    }

    public record DecodedRefreshToken(
            Long userId,
            String userType,
            Long userVersion,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
    }

    private record DecodedActor(
            Long userId,
            String userType,
            Long userVersion
    ) {
    }

    private record IssuedToken(
            String value,
            Instant issuedAt,
            Instant expiresAt
    ) {
    }
}
