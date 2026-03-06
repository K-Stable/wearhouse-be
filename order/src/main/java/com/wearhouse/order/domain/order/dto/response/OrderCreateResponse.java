package com.wearhouse.order.domain.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCreateResponse {

    private final Long orderId;
    private final String orderNo;
    private final String status;
    private final BigDecimal payAmount;
    private final String sagaId;
    private final String outboxEventId;
    private final LocalDateTime orderedAt;
}
