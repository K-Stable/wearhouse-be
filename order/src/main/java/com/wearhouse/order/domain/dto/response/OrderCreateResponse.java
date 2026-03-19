package com.wearhouse.order.domain.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;

@Builder
public record OrderCreateResponse(
        Long orderId,
        String orderNo,
        String status,
        BigDecimal payAmount,
        String sagaId,
        String outboxEventId,
        LocalDateTime orderedAt,
        String paymentKey,
        String paymentId,
        String paymentSessionId,
        String merchantKey,
        String nonce,
        String deadline,
        String payloadHash
) {

    public OrderCreateResponse withStablepaySession(
            String paymentKey,
            String paymentId,
            String paymentSessionId,
            String merchantKey,
            String nonce,
            String deadline,
            String payloadHash
    ) {
        return new OrderCreateResponse(
                orderId,
                orderNo,
                status,
                payAmount,
                sagaId,
                outboxEventId,
                orderedAt,
                paymentKey,
                paymentId,
                paymentSessionId,
                merchantKey,
                nonce,
                deadline,
                payloadHash
        );
    }
}
