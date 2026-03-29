package com.wearhouse.order.buyer.dto.request;

import lombok.Builder;

@Builder
public record OrderCancelRequest(
        String reasonCode
) {
}
