package com.wearhouse.order.buyer.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;

@Builder
public record OrderDetailResponse(
        String orderNo,
        Long buyerId,
        String status,
        String failReasonCode,
        Boolean retryable,
        String nextAction,
        String paymentMethod,
        String recipientName,
        String recipientPhone,
        String zipCode,
        String address1,
        String address2,
        String deliveryRequest,
        BigDecimal itemAmount,
        BigDecimal shippingFee,
        BigDecimal discountAmount,
        BigDecimal pointUsedAmount,
        BigDecimal payAmount,
        LocalDateTime orderedAt,
        List<OrderItemDetailResponse> items
) {

    @Builder
    public record OrderItemDetailResponse(
            Long productId,
            Long optionId,
            String productName,
            String optionName,
            BigDecimal unitPrice,
            Integer quantity,
            BigDecimal lineAmount,
            String status
    ) {
    }
}
