package com.wearhouse.inventory.support.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record InventoryOutboxProperties(
        @Value("${wearhouse.outbox.retry.max-retries:3}") int maxRetries,
        @Value("${wearhouse.outbox.retry.exponential.initial-delay-ms:2000}") long initialDelayMs,
        @Value("${wearhouse.outbox.retry.exponential.max-delay-ms:60000}") long maxDelayMs,
        @Value("${wearhouse.outbox.retry.exponential.multiplier:2.0}") double delayMultiplier,
        @Value("${wearhouse.outbox.send-timeout-ms:3000}") long sendTimeoutMs,
        @Value("${wearhouse.outbox.stale-minutes:10}") int staleMinutes,
        @Value("${wearhouse.outbox.republish-batch-size:100}") int republishBatchSize
) {
}

