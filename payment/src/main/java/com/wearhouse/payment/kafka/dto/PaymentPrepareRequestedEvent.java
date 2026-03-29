package com.wearhouse.payment.kafka.dto;

import java.math.BigDecimal;

public record PaymentPrepareRequestedEvent(
        Long orderId,
        String orderNo,
        BigDecimal amount,
        String paymentMethod
) {
}

