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
        String customerKey,
        String customerId,
        String customerName
) {

    public OrderCreateResponse withStatus(String status) {
        return new OrderCreateResponse(
                orderId,
                orderNo,
                status,
                payAmount,
                sagaId,
                outboxEventId,
                orderedAt,
                customerKey,
                customerId,
                customerName
        );
    }

    public OrderCreateResponse withCustomerContext(
            String customerKey,
            String customerId,
            String customerName
    ) {
        return new OrderCreateResponse(
                orderId,
                orderNo,
                status,
                payAmount,
                sagaId,
                outboxEventId,
                orderedAt,
                customerKey,
                customerId,
                customerName
        );
    }
}
