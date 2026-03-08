package com.wearhouse.user.domain.dto.response;

public record UserAddressResponse(
        Long id,
        String label,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2,
        boolean defaultAddress
) {
}
