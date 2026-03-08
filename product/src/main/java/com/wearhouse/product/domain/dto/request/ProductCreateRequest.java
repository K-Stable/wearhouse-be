package com.wearhouse.product.domain.dto.request;

import com.wearhouse.product.domain.model.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.List;

public record ProductCreateRequest(
        @NotBlank String name,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotBlank String category,
        String description,
        @NotBlank String mainImageUrl,
        List<String> previewImageUrls,
        List<String> detailImageUrls,
        ProductStatus status,
        @NotEmpty List<@Valid ProductOptionCreateRequest> options
) {
}
