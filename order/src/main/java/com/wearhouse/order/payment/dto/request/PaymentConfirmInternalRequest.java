package com.wearhouse.order.payment.dto.request;

import java.math.BigDecimal;

public record PaymentConfirmInternalRequest(
        Long orderId,
        String orderNo,
        String paymentKey,
        BigDecimal amount
) {
}
