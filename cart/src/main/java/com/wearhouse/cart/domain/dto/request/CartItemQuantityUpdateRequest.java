package com.wearhouse.cart.domain.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemQuantityUpdateRequest(
        @NotNull @Min(1) Integer quantity
) {
}
