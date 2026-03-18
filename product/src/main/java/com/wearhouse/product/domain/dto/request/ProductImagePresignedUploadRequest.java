package com.wearhouse.product.domain.dto.request;

import com.wearhouse.product.domain.model.ProductImageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ProductImagePresignedUploadRequest(
        @NotBlank
        String fileName,

        @NotBlank
        String contentType,

        @NotNull
        ProductImageType imageType
) {
}
