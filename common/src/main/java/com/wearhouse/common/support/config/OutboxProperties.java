package com.wearhouse.common.support.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.outbox")
public record OutboxProperties(
        @Positive
        @DefaultValue("3000")
        long sendTimeoutMs,

        @Positive
        @DefaultValue("10")
        int staleMinutes,

        @Positive
        @DefaultValue("100")
        int republishBatchSize
) {
}
