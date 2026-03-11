package com.wearhouse.auth.infra.redis;

import com.wearhouse.auth.support.config.AuthRefreshTokenProperties;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final AuthRefreshTokenProperties refreshTokenProperties;

    public void save(String userType, String refreshToken, Long userId) {
        Duration refreshTtl = Duration.ofDays(refreshTokenProperties.ttlDays());
        redisTemplate.opsForValue().set(refreshKey(userType, refreshToken), String.valueOf(userId), refreshTtl);
    }

    public Optional<Long> findUserId(String userType, String refreshToken) {
        String value = redisTemplate.opsForValue().get(refreshKey(userType, refreshToken));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public Optional<Long> consumeUserId(String userType, String refreshToken) {
        String value = redisTemplate.opsForValue().getAndDelete(refreshKey(userType, refreshToken));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    public void delete(String userType, String refreshToken) {
        redisTemplate.delete(refreshKey(userType, refreshToken));
    }

    private String refreshKey(String userType, String refreshToken) {
        String keyUserType = userType == null ? "" : userType.toLowerCase();
        return refreshTokenProperties.keyPrefix() + keyUserType + ":" + refreshToken;
    }
}
