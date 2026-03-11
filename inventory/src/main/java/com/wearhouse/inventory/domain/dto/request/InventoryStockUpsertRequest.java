package com.wearhouse.inventory.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import org.hibernate.validator.constraints.URL;

public record InventoryStockUpsertRequest(
        @NotNull Long skuId,
        @NotNull @Min(0) Integer availableQty,
        @NotNull Long sellerId,
        @NotNull Long productId,
        @NotBlank String productName,
        @NotNull @PositiveOrZero BigDecimal productPrice,
        @NotBlank String category,
        @NotBlank String size,
        @NotBlank String color,
        @NotBlank @URL String mainImageUrl,
        @NotNull @Min(0) @Max(1) Integer status
) {
}
