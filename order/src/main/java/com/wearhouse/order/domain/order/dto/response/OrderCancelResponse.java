package com.wearhouse.order.domain.order.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderCancelResponse {

    private final String orderNo;
    private final String status;
    private final String reasonCode;
    private final LocalDateTime cancelledAt;
}
