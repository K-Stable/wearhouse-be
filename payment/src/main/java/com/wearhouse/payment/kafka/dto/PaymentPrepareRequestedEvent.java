package com.wearhouse.payment.kafka.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentPrepareRequestedEvent(
        Long orderId,
        String orderNo,
        Long buyerId,
        BigDecimal amount,
        String paymentMethod
) {
}
