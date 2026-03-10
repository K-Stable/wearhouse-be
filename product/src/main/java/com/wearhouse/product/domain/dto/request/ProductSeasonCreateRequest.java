package com.wearhouse.product.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProductSeasonCreateRequest (
    @NotBlank
    @Size(max = 150)
    String name
){
 }


