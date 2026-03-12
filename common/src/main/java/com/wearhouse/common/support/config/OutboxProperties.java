package com.wearhouse.common.support.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
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
    private long republishIntervalMs = 60000;

    @Positive
    private int republishBatchSize = 100;

    @Valid
    private Retry retry = new Retry();

    public long sendTimeoutMs() {
        return sendTimeoutMs;
    }

    public int staleMinutes() {
        return staleMinutes;
    }

    public long republishIntervalMs() {
        return republishIntervalMs;
    }

    public int republishBatchSize() {
        return republishBatchSize;
    }

    public int maxRetries() {
        return retry.maxRetries;
    }

    public long initialDelayMs() {
        return retry.exponential.initialDelayMs;
    }

    public long maxDelayMs() {
        return retry.exponential.maxDelayMs;
    }

    public double delayMultiplier() {
        return retry.exponential.multiplier;
    }

    @Getter
    @Setter
    public static class Retry {

        @Positive
        private int maxRetries = 3;

        @Valid
        private Exponential exponential = new Exponential();
    }

    @Getter
    @Setter
    public static class Exponential {

        @Positive
        private long initialDelayMs = 2000;

        @Positive
        private long maxDelayMs = 60000;

        @DecimalMin("1.0")
        private double multiplier = 2.0;
    }
}
