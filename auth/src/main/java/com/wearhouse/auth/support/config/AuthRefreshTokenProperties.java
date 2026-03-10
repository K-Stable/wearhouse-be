package com.wearhouse.auth.support.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AuthRefreshTokenProperties(
        @Value("${wearhouse.auth.refresh.ttl-days:14}") long ttlDays,
        @Value("${wearhouse.auth.refresh.key-prefix:rt:}") String keyPrefix
) {
}
