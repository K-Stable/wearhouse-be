package com.wearhouse.product.domain.dto.response;

import java.math.BigDecimal;

public record ProductOptionResponse(
        Long optionId,
        String size,
        String color
) {
}
