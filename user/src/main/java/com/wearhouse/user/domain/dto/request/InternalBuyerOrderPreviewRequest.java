package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.NotNull;

public record InternalBuyerOrderPreviewRequest(
        @NotNull Long buyerId
) {
}
