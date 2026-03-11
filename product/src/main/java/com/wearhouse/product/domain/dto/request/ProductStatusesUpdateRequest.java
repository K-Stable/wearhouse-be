package com.wearhouse.product.domain.dto.request;

import com.wearhouse.product.domain.model.ProductStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ProductStatusesUpdateRequest(
        @NotEmpty List<@NotNull Long> productIds,
        @NotNull ProductStatus status
) {
}

