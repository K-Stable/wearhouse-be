package com.wearhouse.apigateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.apigateway.security.dto.PassportContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class PassportCacheService {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration cacheTtl;
    private final Duration userIndexTtl;

    public PassportCacheService(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${wearhouse.gateway.passport.cache-ttl-seconds:60}") long cacheTtlSeconds,
            @Value("${wearhouse.gateway.passport.user-index-ttl-seconds:1800}") long userIndexTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheTtl = Duration.ofSeconds(cacheTtlSeconds);
        this.userIndexTtl = Duration.ofSeconds(userIndexTtlSeconds);
    }

    public Optional<PassportContext> get(String accessToken) {
        String cached = redisTemplate.opsForValue().get(passportKeyFromToken(accessToken));
        if (cached == null || cached.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(cached, PassportContext.class));
        } catch (Exception exception) {
            redisTemplate.delete(passportKeyFromToken(accessToken));
            return Optional.empty();
        }
    }

    public void put(String accessToken, PassportContext passportContext) {
        try {
            String tokenHash = hash(accessToken);
            redisTemplate.opsForValue().set(
                    passportKeyFromHash(tokenHash),
                    objectMapper.writeValueAsString(passportContext),
                    cacheTtl
            );
            String userIndexKey = userIndexKey(passportContext.userType(), passportContext.userId());
            redisTemplate.opsForSet().add(userIndexKey, tokenHash);
            redisTemplate.expire(userIndexKey, userIndexTtl);
        } catch (Exception exception) {
            throw new IllegalStateException("Passport 캐시 저장에 실패했습니다.", exception);
        }
    }

    public void evictByUser(String userType, Long userId) {
        String userIndexKey = userIndexKey(userType, userId);
        Set<String> tokenHashes = redisTemplate.opsForSet().members(userIndexKey);
        if (tokenHashes == null || tokenHashes.isEmpty()) {
            redisTemplate.delete(userIndexKey);
            return;
        }

        List<String> passportKeys = tokenHashes.stream()
                .map(this::passportKeyFromHash)
                .toList();
        if (!passportKeys.isEmpty()) {
            redisTemplate.delete(passportKeys);
        }
        redisTemplate.delete(userIndexKey);
    }

    private String passportKeyFromToken(String accessToken) {
        return "psp:v1:" + hash(accessToken);
    }

    private String passportKeyFromHash(String tokenHash) {
        return "psp:v1:" + tokenHash;
    }

    private String userIndexKey(String userType, Long userId) {
        return "psp:idx:v1:" + userType.toLowerCase() + ":" + userId;
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("토큰 해시 계산에 실패했습니다.", exception);
        }
    }
}
