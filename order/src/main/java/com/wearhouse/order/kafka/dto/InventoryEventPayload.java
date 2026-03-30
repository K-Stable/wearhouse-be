package com.wearhouse.order.kafka.dto;

public record InventoryEventPayload(
        Long orderId,
        String orderNo,
        String reasonCode
) {
}

