package com.wearhouse.product.domain.dto.request;

import com.wearhouse.product.domain.model.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusUpdateRequest(
        @NotNull ProductStatus status
) {
}
