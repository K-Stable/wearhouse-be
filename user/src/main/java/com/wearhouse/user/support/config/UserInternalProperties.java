package com.wearhouse.user.support.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "wearhouse.user.internal")
public record UserInternalProperties(
        String sharedSecret
) {

    private static final String DEFAULT_SHARED_SECRET = "wearhouse-user-internal-secret";

    public String resolvedSharedSecret() {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            return DEFAULT_SHARED_SECRET;
        }
        return sharedSecret;
    }
}
