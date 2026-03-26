package com.wearhouse.order.domain.dto.response;

import java.time.LocalDate;

public record SellerOrderListItemResponse(
        String orderNo,
        Long buyerId,
        Integer quantity,
        String paymentMethod,
        LocalDate orderedDate,
        String status
) {
}
