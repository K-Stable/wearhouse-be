package com.wearhouse.auth.support.jwt;

import com.wearhouse.auth.domain.model.AuthUserType;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final String issuer;
    private final long accessTokenMinutes;

    public JwtTokenProvider(
            @Value("${wearhouse.auth.jwt.secret}") String secret,
            @Value("${wearhouse.auth.jwt.issuer:wearhouse-auth}") String issuer,
            @Value("${wearhouse.auth.jwt.access-token-minutes:30}") long accessTokenMinutes
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    public IssuedAccessToken issueAccessToken(Long userId, AuthUserType userType, List<String> roles, Long userVersion) {
        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(accessTokenMinutes);

        String token = Jwts.builder()
                .issuer(issuer)
                .subject(String.valueOf(userId))
                .claim("userType", userType.name())
                .claim("roles", roles)
                .claim("userVersion", userVersion)
                .claim("tokenType", "ACCESS")
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt.toInstant(ZoneOffset.UTC)))
                .expiration(Date.from(expiresAt.toInstant(ZoneOffset.UTC)))
                .signWith(secretKey)
                .compact();

        return new IssuedAccessToken(token, issuedAt, expiresAt);
    }

    public DecodedAccessToken decodeAccessToken(String accessToken) {
        Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(accessToken)
                .getPayload();

        if (!"ACCESS".equals(claims.get("tokenType", String.class))) {
            throw new IllegalStateException("유효하지 않은 토큰 유형입니다.");
        }

        @SuppressWarnings("unchecked")
        List<String> roles = claims.get("roles", List.class);

        return new DecodedAccessToken(
                Long.parseLong(claims.getSubject()),
                AuthUserType.valueOf(claims.get("userType", String.class)),
                roles,
                Long.parseLong(String.valueOf(claims.get("userVersion"))),
                claims.getIssuedAt().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime(),
                claims.getExpiration().toInstant().atOffset(ZoneOffset.UTC).toLocalDateTime()
        );
    }

    public record IssuedAccessToken(
            String accessToken,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
    }

    public record DecodedAccessToken(
            Long userId,
            AuthUserType userType,
            List<String> roles,
            Long userVersion,
            LocalDateTime issuedAt,
            LocalDateTime expiresAt
    ) {
    }
}
