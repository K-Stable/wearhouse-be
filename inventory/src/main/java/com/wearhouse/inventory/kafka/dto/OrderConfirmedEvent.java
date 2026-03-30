package com.wearhouse.inventory.kafka.dto;

public record OrderConfirmedEvent(
        Long orderId,
        String orderNo
) {
}

