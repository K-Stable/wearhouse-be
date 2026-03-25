package com.wearhouse.payment.domain.payment.dto.response;

public record PaymentPrepareResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}
