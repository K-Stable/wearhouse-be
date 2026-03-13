package com.wearhouse.user.domain.dto.response;

import java.math.BigDecimal;

public record InternalBuyerOrderPreviewResponse(
        Long buyerId,
        BigDecimal point,
        BuyerDefaultAddressResponse defaultAddress
) {
    public record BuyerDefaultAddressResponse(
            Long addressId,
            String label,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2
    ) {
    }
}
