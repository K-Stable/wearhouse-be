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
import org.hibernate.validator.constraints.URL;

public record ProductCreateRequest(
        @NotBlank
        @Size(max = 150)
        String name,

        @NotNull @PositiveOrZero
        BigDecimal price,

        @NotNull
        Category category,

        String description,

        @NotBlank
        @URL
        String mainImageUrl,

        List<@NotBlank @URL String>
        previewImageUrls,

        List<@NotBlank @URL String>
        detailImageUrls,

        ProductStatus status,

        @NotEmpty
        List<@Valid ProductOptionCreateRequest> options
) {
}
