package com.wearhouse.product.domain.dto.request;

import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record ProductCreateRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @NotNull @PositiveOrZero
        BigDecimal price,

        @NotNull
        Category category,

        String details,

        String sizeGuide,

        String shipping,

        @NotEmpty
        List<@Valid ProductOptionCreateRequest> options,

        @NotBlank
        String mainImageUrl,

        List<@NotBlank String> previewImageUrls,

        List<@NotBlank String> detailImageUrls,

        ProductStatus status

) {
}
