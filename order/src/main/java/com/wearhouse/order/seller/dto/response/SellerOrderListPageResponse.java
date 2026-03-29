package com.wearhouse.order.seller.dto.response;

import java.util.List;

public record SellerOrderListPageResponse(
        List<SellerOrderListItemResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
