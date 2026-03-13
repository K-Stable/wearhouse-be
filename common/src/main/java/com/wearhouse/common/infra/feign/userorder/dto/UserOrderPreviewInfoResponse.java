package com.wearhouse.common.infra.feign.userorder.dto;

import java.math.BigDecimal;

public record UserOrderPreviewInfoResponse(
        Long buyerId,
        BigDecimal point,
        BuyerDefaultAddress defaultAddress
) {
    public record BuyerDefaultAddress(
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
