package com.wearhouse.payment.domain.payment.dto.response;

public record WalletPrepareResponse(
        String checkoutSessionId,
        String checkoutUrl,
        String appLaunchUrl,
        String checkoutExpiresAt
) {
}
