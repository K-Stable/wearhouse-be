package com.wearhouse.order.domain.order.dto.request;

import lombok.Builder;

@Builder
public record OrderCancelRequest(
        String reasonCode
) {
}
