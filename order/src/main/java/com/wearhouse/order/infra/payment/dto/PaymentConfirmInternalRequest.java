package com.wearhouse.order.infra.payment.dto;

import java.math.BigDecimal;

public record PaymentConfirmInternalRequest(
        Long orderId,
        String orderNo,
        String paymentKey,
        BigDecimal amount
) {
}
