package com.wearhouse.order.kafka.dto;

public record PaymentEventPayload(
        Long orderId,
        String orderNo,
        String reasonCode
) {
}

