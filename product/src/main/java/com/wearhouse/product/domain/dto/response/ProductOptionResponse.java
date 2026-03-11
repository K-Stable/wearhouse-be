package com.wearhouse.product.domain.dto.response;

public record ProductOptionResponse(
        Long optionId,
        String size,
        String color,
        Integer stockQuantity
) {
}
