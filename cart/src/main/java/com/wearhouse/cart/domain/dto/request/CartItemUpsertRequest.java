package com.wearhouse.cart.domain.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import org.hibernate.validator.constraints.URL;

public record CartItemUpsertRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = 150) String productName,
        @NotBlank @URL @Size(max = 500) String mainImageUrl,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotEmpty List<@Valid CartOptionRequest> items
) {
    public record CartOptionRequest(
            @NotNull Long optionId,
            @NotBlank @Size(max = 60) String size,
            @NotBlank @Size(max = 60) String color,
            @NotNull @Min(1) Integer quantity
    ) {
    }
}
