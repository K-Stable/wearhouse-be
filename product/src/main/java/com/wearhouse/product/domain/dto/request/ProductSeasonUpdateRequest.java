package com.wearhouse.product.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSeasonUpdateRequest(
        @NotBlank
        @Size(max = 150)
        String name
) {
}
