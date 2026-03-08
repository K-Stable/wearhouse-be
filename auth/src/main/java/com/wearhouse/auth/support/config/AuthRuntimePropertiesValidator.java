package com.wearhouse.auth.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthRuntimePropertiesValidator {

    private final String jwtSecret;
    private final String internalSharedSecret;
    private final long accessTokenMinutes;

    public AuthRuntimePropertiesValidator(
            @Value("${wearhouse.auth.jwt.secret:}") String jwtSecret,
            @Value("${wearhouse.auth.internal.shared-secret:}") String internalSharedSecret,
            @Value("${wearhouse.auth.jwt.access-token-minutes:0}") long accessTokenMinutes
    ) {
        this.jwtSecret = jwtSecret;
        this.internalSharedSecret = internalSharedSecret;
        this.accessTokenMinutes = accessTokenMinutes;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.auth.jwt.secret", jwtSecret);
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("wearhouse.auth.jwt.secret 길이는 최소 32자 이상이어야 합니다.");
        }
        requireText("wearhouse.auth.internal.shared-secret", internalSharedSecret);
        requirePositive("wearhouse.auth.jwt.access-token-minutes", accessTokenMinutes);
    }

    private void requireText(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 값은 비어 있을 수 없습니다.");
        }
    }

    private void requirePositive(String key, long value) {
        if (value <= 0) {
            throw new IllegalStateException(key + " 값은 1 이상이어야 합니다.");
        }
    }
}
