package com.wearhouse.payment.domain.payment.dto.response;

public record StablepaySessionPrepareResponse(
        String paymentKey,
        String paymentId,
        String paymentSessionId,
        String merchantKey,
        String nonce,
        String deadline,
        String payloadHash
) {
}
