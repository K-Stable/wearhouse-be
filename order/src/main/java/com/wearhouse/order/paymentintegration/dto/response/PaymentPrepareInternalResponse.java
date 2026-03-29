package com.wearhouse.order.paymentintegration.dto.response;

public record PaymentPrepareInternalResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}
