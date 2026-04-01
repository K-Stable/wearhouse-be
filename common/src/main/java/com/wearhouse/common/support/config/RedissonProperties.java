package com.wearhouse.common.support.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.redisson")
public record RedissonProperties(
        @Positive
        @DefaultValue("30000")
        long lockWatchdogTimeoutMs
) {
}
