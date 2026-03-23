package com.wearhouse.inventory.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class InventoryRuntimePropertiesValidator {

    private final InventoryKafkaTopicsProperties inventoryKafkaTopicsProperties;
    private final InventoryProperties inventoryProperties;

    public InventoryRuntimePropertiesValidator(
            InventoryKafkaTopicsProperties inventoryKafkaTopicsProperties,
            InventoryProperties inventoryProperties
    ) {
        this.inventoryKafkaTopicsProperties = inventoryKafkaTopicsProperties;
        this.inventoryProperties = inventoryProperties;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.inventory-command-topic", inventoryKafkaTopicsProperties.getInventoryCommandTopic());
        requireText("wearhouse.kafka.inventory-event-topic", inventoryKafkaTopicsProperties.getInventoryEventTopic());
        requireText("wearhouse.kafka.order-event-topic", inventoryKafkaTopicsProperties.getOrderEventTopic());
        requirePositive("wearhouse.inventory.reservation-hold-minutes", inventoryProperties.getReservationHoldMinutes());
        requirePositive("wearhouse.inventory.optimistic-retry-count", inventoryProperties.getOptimisticRetryCount());
        requirePositive("wearhouse.inventory.reservation-expire-interval-ms", inventoryProperties.getReservationExpireIntervalMs());
        requirePositive("wearhouse.inventory.reservation-expire-batch-size", inventoryProperties.getReservationExpireBatchSize());
        requirePositive("wearhouse.inventory.lock.wait-time-ms", inventoryProperties.getLock().getWaitTimeMs());
        requirePositive("wearhouse.inventory.lock.lease-time-ms", inventoryProperties.getLock().getLeaseTimeMs());
        requirePositive("wearhouse.inventory.lock.retry-interval-ms", inventoryProperties.getLock().getRetryIntervalMs());
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
