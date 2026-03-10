package com.wearhouse.auth.support.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record AuthInternalSharedSecret(
        @Value("${wearhouse.auth.internal.shared-secret}") String value
) {
}
