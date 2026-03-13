package com.wearhouse.order.domain.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record OrderPreviewResponse(
        List<OrderableItem> orderableItems,
        List<UnavailableItem> unavailableItems,
        boolean allSoldOut,
        boolean partialSoldOut,
        String message,
        BigDecimal itemAmount,
        BigDecimal shippingFee,
        BigDecimal payAmount,
        BuyerOrderPreviewInfo buyerInfo
) {
    public record OrderableItem(
            Long productId,
            Long optionId,
            Long sellerId,
            String productName,
            String color,
            String size,
            String mainImageUrl,
            String productStatus,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal lineAmount
    ) {
    }

    public record UnavailableItem(
            Long productId,
            String color,
            String size,
            Integer requestedQuantity,
            Integer availableQuantity,
            String reason
    ) {
    }

    public record BuyerOrderPreviewInfo(
            BigDecimal point,
            DefaultAddress defaultAddress
    ) {
    }

    public record DefaultAddress(
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
