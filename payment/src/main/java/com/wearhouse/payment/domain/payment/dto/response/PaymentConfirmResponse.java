package com.wearhouse.payment.domain.payment.dto.response;

public record PaymentConfirmResponse(
        Long orderId,
        String orderNo,
        String paymentId,
        String paymentStatus,
        String commandStatus,
        String reasonCode
) {
}
