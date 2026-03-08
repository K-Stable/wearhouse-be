package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddressCreateRequest(
        String label,
        @NotBlank String recipientName,
        @NotBlank String recipientPhone,
        @NotBlank String zipCode,
        @NotBlank String address1,
        String address2,
        Boolean defaultAddress
) {
}
