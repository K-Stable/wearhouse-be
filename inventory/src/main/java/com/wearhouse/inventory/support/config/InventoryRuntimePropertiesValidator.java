package com.wearhouse.inventory.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class InventoryRuntimePropertiesValidator {

    private final String inventoryCommandTopic;
    private final String inventoryEventTopic;
    private final String orderEventTopic;
    private final int reservationHoldMinutes;
    private final int optimisticRetryCount;
    private final long reservationExpireIntervalMs;
    private final int reservationExpireBatchSize;
    private final long lockWaitTimeMs;
    private final long lockLeaseTimeMs;
    private final long lockRetryIntervalMs;

    public InventoryRuntimePropertiesValidator(
            @Value("${wearhouse.kafka.inventory-command-topic:}") String inventoryCommandTopic,
            @Value("${wearhouse.kafka.inventory-event-topic:}") String inventoryEventTopic,
            @Value("${wearhouse.kafka.order-event-topic:}") String orderEventTopic,
            @Value("${wearhouse.inventory.reservation-hold-minutes:0}") int reservationHoldMinutes,
            @Value("${wearhouse.inventory.optimistic-retry-count:0}") int optimisticRetryCount,
            @Value("${wearhouse.inventory.reservation-expire-interval-ms:0}") long reservationExpireIntervalMs,
            @Value("${wearhouse.inventory.reservation-expire-batch-size:0}") int reservationExpireBatchSize,
            @Value("${wearhouse.inventory.lock.wait-time-ms:0}") long lockWaitTimeMs,
            @Value("${wearhouse.inventory.lock.lease-time-ms:0}") long lockLeaseTimeMs,
            @Value("${wearhouse.inventory.lock.retry-interval-ms:0}") long lockRetryIntervalMs
    ) {
        this.inventoryCommandTopic = inventoryCommandTopic;
        this.inventoryEventTopic = inventoryEventTopic;
        this.orderEventTopic = orderEventTopic;
        this.reservationHoldMinutes = reservationHoldMinutes;
        this.optimisticRetryCount = optimisticRetryCount;
        this.reservationExpireIntervalMs = reservationExpireIntervalMs;
        this.reservationExpireBatchSize = reservationExpireBatchSize;
        this.lockWaitTimeMs = lockWaitTimeMs;
        this.lockLeaseTimeMs = lockLeaseTimeMs;
        this.lockRetryIntervalMs = lockRetryIntervalMs;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.inventory-command-topic", inventoryCommandTopic);
        requireText("wearhouse.kafka.inventory-event-topic", inventoryEventTopic);
        requireText("wearhouse.kafka.order-event-topic", orderEventTopic);
        requirePositive("wearhouse.inventory.reservation-hold-minutes", reservationHoldMinutes);
        requirePositive("wearhouse.inventory.optimistic-retry-count", optimisticRetryCount);
        requirePositive("wearhouse.inventory.reservation-expire-interval-ms", reservationExpireIntervalMs);
        requirePositive("wearhouse.inventory.reservation-expire-batch-size", reservationExpireBatchSize);
        requirePositive("wearhouse.inventory.lock.wait-time-ms", lockWaitTimeMs);
        requirePositive("wearhouse.inventory.lock.lease-time-ms", lockLeaseTimeMs);
        requirePositive("wearhouse.inventory.lock.retry-interval-ms", lockRetryIntervalMs);
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
