package com.wearhouse.payment.internal.dto.response;

public record PaymentConfirmResponse(
        Long orderId,
        String orderNo,
        String paymentId,
        String paymentStatus,
        String commandStatus,
        String reasonCode
) {
}
