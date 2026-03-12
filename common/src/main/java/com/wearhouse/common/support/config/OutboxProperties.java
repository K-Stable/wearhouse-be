package com.wearhouse.common.support.config;

import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "wearhouse.outbox")
public class OutboxProperties {

    @Positive
    private long sendTimeoutMs = 3000;

    @Positive
    private int staleMinutes = 10;

    @Positive
    private int republishBatchSize = 100;

    public long sendTimeoutMs() {
        return sendTimeoutMs;
    }

    public int staleMinutes() {
        return staleMinutes;
    }

    public int republishBatchSize() {
        return republishBatchSize;
    }
}
