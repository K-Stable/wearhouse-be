package com.wearhouse.order.domain.dto.request;

import lombok.Builder;

@Builder
public record OrderCancelRequest(
        String reasonCode
) {
}
