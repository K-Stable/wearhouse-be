package com.wearhouse.order.payment.dto.response;

public record PaymentConfirmInternalResponse(
        Long orderId,
        String orderNo,
        String paymentId,
        String paymentStatus,
        String commandStatus,
        String reasonCode
) {
}
