package com.wearhouse.order.infra.payment.dto;

public record PaymentConfirmInternalResponse(
        Long orderId,
        String orderNo,
        String paymentId,
        String paymentStatus,
        String commandStatus,
        String reasonCode
) {
}
