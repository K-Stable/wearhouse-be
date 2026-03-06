package com.wearhouse.order.domain.order.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OrderDetailResponse {

    private final String orderNo;
    private final Long buyerId;
    private final String status;
    private final String paymentMethod;
    private final String recipientName;
    private final String recipientPhone;
    private final String zipCode;
    private final String address1;
    private final String address2;
    private final String deliveryRequest;
    private final BigDecimal itemAmount;
    private final BigDecimal shippingFee;
    private final BigDecimal discountAmount;
    private final BigDecimal pointUsedAmount;
    private final BigDecimal payAmount;
    private final LocalDateTime orderedAt;

    @Builder.Default
    private final List<OrderItemDetailResponse> items = new ArrayList<>();

    @Getter
    @Builder
    public static class OrderItemDetailResponse {

        private final Long productId;
        private final Long optionId;
        private final String productName;
        private final String optionName;
        private final BigDecimal unitPrice;
        private final Integer quantity;
        private final BigDecimal lineAmount;
        private final String status;
    }
}
