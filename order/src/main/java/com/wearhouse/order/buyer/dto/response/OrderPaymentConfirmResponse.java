package com.wearhouse.order.buyer.dto.response;

public record OrderPaymentConfirmResponse(
        Long orderId,
        String orderNo,
        String status,
        String reasonCode
) {
}
