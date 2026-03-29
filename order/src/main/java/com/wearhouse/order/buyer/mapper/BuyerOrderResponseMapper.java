package com.wearhouse.order.buyer.mapper;

import com.wearhouse.common.infra.feign.inventory.dto.InventoryOrderPreviewResponse.InventoryOrderPreviewLine;
import com.wearhouse.common.infra.feign.userorder.dto.UserOrderPreviewInfoResponse;
import com.wearhouse.order.buyer.dto.response.OrderDetailResponse;
import com.wearhouse.order.buyer.dto.response.OrderDetailResponse.OrderItemDetailResponse;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.BuyerOrderPreviewInfo;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.DefaultAddress;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.OrderableItem;
import com.wearhouse.order.buyer.dto.response.OrderPreviewResponse.UnavailableItem;
import com.wearhouse.order.buyer.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderItemEntity;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class BuyerOrderResponseMapper {

    public OrderDetailResponse toOrderDetailResponse(
            OrderEntity order,
            boolean retryable,
            String nextAction,
            List<OrderItemDetailResponse> detailItems
    ) {
        OrderInfo info = order.getOrderInfo();
        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .status(order.getStatus().name())
                .failReasonCode(order.getFailReasonCode())
                .retryable(retryable)
                .nextAction(nextAction)
                .paymentMethod(info == null || info.getPaymentMethod() == null ? null : info.getPaymentMethod().name())
                .recipientName(info == null ? null : info.getRecipientName())
                .recipientPhone(info == null ? null : info.getRecipientPhone())
                .zipCode(info == null ? null : info.getZipCode())
                .address1(info == null ? null : info.getAddress1())
                .address2(info == null ? null : info.getAddress2())
                .deliveryRequest(info == null ? null : info.getDeliveryRequest())
                .itemAmount(order.getItemAmount())
                .shippingFee(order.getShippingFee())
                .discountAmount(order.getDiscountAmount())
                .pointUsedAmount(order.getPointUsedAmount())
                .payAmount(order.getTotalAmount())
                .orderedAt(order.getOrderedAt())
                .items(detailItems)
                .build();
    }

    public List<OrderItemDetailResponse> toDetailItemResponses(List<OrderItemEntity> orderItems) {
        return orderItems.stream()
                .map(this::toDetailItemResponse)
                .toList();
    }

    public OrderSummaryResponse toOrderSummaryResponse(OrderEntity order) {
        return OrderSummaryResponse.builder()
                .orderNo(order.getOrderNo())
                .status(order.getStatus().name())
                .payAmount(order.getTotalAmount())
                .orderedAt(order.getOrderedAt())
                .build();
    }

    public OrderableItem toOrderableItem(InventoryOrderPreviewLine item, BigDecimal lineAmount) {
        return new OrderableItem(
                item.productId(),
                item.optionId(),
                item.sellerId(),
                item.productName(),
                item.color(),
                item.size(),
                item.mainImageUrl(),
                item.productStatus(),
                item.requestedQuantity(),
                item.unitPrice(),
                lineAmount
        );
    }

    public UnavailableItem toUnavailableItem(InventoryOrderPreviewLine item, String reason) {
        return new UnavailableItem(
                item.productId(),
                item.color(),
                item.size(),
                item.requestedQuantity(),
                item.availableQuantity(),
                reason
        );
    }

    public OrderPreviewResponse toOrderPreviewResponse(
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
        return new OrderPreviewResponse(
                orderableItems,
                unavailableItems,
                allSoldOut,
                partialSoldOut,
                message,
                itemAmount,
                shippingFee,
                payAmount,
                buyerInfo
        );
    }

    public BuyerOrderPreviewInfo toBuyerPreviewInfo(UserOrderPreviewInfoResponse data) {
        DefaultAddress defaultAddress = data.defaultAddress() == null ? null : new DefaultAddress(
                data.defaultAddress().addressId(),
                data.defaultAddress().label(),
                data.defaultAddress().recipientName(),
                data.defaultAddress().recipientPhone(),
                data.defaultAddress().zipCode(),
                data.defaultAddress().address1(),
                data.defaultAddress().address2()
        );
        return new BuyerOrderPreviewInfo(data.point(), defaultAddress);
    }

    private OrderItemDetailResponse toDetailItemResponse(OrderItemEntity orderItem) {
        return OrderItemDetailResponse.builder()
                .productId(orderItem.getProductId())
                .optionId(orderItem.getOptionId())
                .productName(orderItem.getProductNameSnapshot())
                .optionName(orderItem.getOptionNameSnapshot())
                .unitPrice(orderItem.getUnitPrice())
                .quantity(orderItem.getQuantity())
                .lineAmount(orderItem.getLineAmount())
                .status(orderItem.getStatus().name())
                .build();
    }
}
