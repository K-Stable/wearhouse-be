package com.wearhouse.inventory.kafka.dto;

public record InventoryReleaseRequestedEvent(
        Long orderId,
        String orderNo,
        String reasonCode
) {
}

