package com.wearhouse.auth.infra.redis;

import com.wearhouse.auth.domain.model.AuthUserType;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenStore {

    private final StringRedisTemplate redisTemplate;
    private final Duration refreshTtl;
    private final String keyPrefix;

    public RefreshTokenStore(
            StringRedisTemplate redisTemplate,
            @Value("${wearhouse.auth.refresh.ttl-days:14}") long refreshTtlDays,
            @Value("${wearhouse.auth.refresh.key-prefix:rt:}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.refreshTtl = Duration.ofDays(refreshTtlDays);
        this.keyPrefix = keyPrefix;
    }

    public String issue(AuthUserType userType, Long userId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(key(userType, token), String.valueOf(userId), refreshTtl);
        return token;
    }

    public Optional<Long> resolve(AuthUserType userType, String token) {
        String value = redisTemplate.opsForValue().get(key(userType, token));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Long.parseLong(value));
    }

    public void revoke(AuthUserType userType, String token) {
        redisTemplate.delete(key(userType, token));
    }

    private String key(AuthUserType userType, String token) {
        return keyPrefix + userType.key() + ":" + token;
    }
}
