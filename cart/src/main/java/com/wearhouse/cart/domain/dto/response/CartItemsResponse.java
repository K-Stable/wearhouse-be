package com.wearhouse.cart.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartItemsResponse(
        BigDecimal totalPrice,
        List<CartItemResponse> items
) {
}
