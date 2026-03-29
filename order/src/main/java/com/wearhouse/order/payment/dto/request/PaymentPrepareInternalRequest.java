package com.wearhouse.order.payment.dto.request;

import java.math.BigDecimal;

public record PaymentPrepareInternalRequest(
        Long orderId,
        String orderNo,
        String customerId,
        String orderName,
        BigDecimal amount,
        String successUrl,
        String failUrl,
        String idempotencyKey
) {
}
