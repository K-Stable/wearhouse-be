package com.wearhouse.product.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductOptionCreateRequest(
        @NotBlank
        @Size(max = 60)
        String size,

        @NotBlank
        @Size(max = 60)
        String color,

        @NotNull @PositiveOrZero Integer stockQuantity,
        @PositiveOrZero BigDecimal additionalPrice
) {
}
