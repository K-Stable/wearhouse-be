package com.wearhouse.order.domain.dto.response;

public record OrderPaymentConfirmResponse(
        Long orderId,
        String orderNo,
        String status,
        String reasonCode
) {
}
