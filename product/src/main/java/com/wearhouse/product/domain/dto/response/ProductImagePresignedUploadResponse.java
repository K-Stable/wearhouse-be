package com.wearhouse.product.domain.dto.response;

public record ProductImagePresignedUploadResponse(
        String imageKey,
        String uploadUrl,
        String imageUrl
) {
}
