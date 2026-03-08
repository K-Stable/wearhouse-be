package com.wearhouse.product.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;

public record ProductOptionCreateRequest(
        @NotBlank String size,
        @NotBlank String color,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @PositiveOrZero BigDecimal additionalPrice
) {
}
