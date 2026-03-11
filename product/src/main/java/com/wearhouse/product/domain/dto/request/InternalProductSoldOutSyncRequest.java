package com.wearhouse.product.domain.dto.request;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InternalProductSoldOutSyncRequest(
        @NotEmpty List<@NotNull Long> productIds
) {
}
