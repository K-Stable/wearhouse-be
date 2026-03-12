package com.wearhouse.order.domain.service.query;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderDetailResponse.OrderItemDetailResponse;
import com.wearhouse.order.domain.dto.response.OrderSummaryResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderItemEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.ReadTx;

@Service
@RequiredArgsConstructor
public class OrderQueryService {

    private static final String NEXT_ACTION_VIEW_ORDER = "VIEW_ORDER";
    private static final String NEXT_ACTION_RETURN_TO_CHECKOUT = "RETURN_TO_CHECKOUT";
    private static final Set<OrderStatus> RETRYABLE_STATUSES = Set.of(
            OrderStatus.RESERVE_FAILED,
            OrderStatus.PAYMENT_FAILED
    );

    private final OrderRepository orderRepository;

    @ReadTx
    public OrderDetailResponse getOrderDetail(String orderNo) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));

        List<OrderItemDetailResponse> detailItems = mapDetailItems(order.getItems());
        OrderInfo info = order.getOrderInfo();
        boolean retryable = RETRYABLE_STATUSES.contains(order.getStatus());
        return OrderDetailResponse.builder()
                .orderNo(order.getOrderNo())
                .buyerId(order.getBuyerId())
                .status(order.getStatus().name())
                .failReasonCode(order.getFailReasonCode())
                .retryable(retryable)
                .nextAction(retryable ? NEXT_ACTION_RETURN_TO_CHECKOUT : NEXT_ACTION_VIEW_ORDER)
                .paymentMethod(info == null ? null : info.getPaymentMethod())
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

    @ReadTx
    public List<OrderSummaryResponse> getBuyerOrders(Long buyerId, int limit) {
        List<OrderEntity> orders = orderRepository.findByBuyerIdOrderByIdDesc(buyerId, PageRequest.of(0, limit));
        List<OrderSummaryResponse> responses = new ArrayList<>();
        for (OrderEntity order : orders) {
            responses.add(OrderSummaryResponse.builder()
                    .orderNo(order.getOrderNo())
                    .status(order.getStatus().name())
                    .payAmount(order.getTotalAmount())
                    .orderedAt(order.getOrderedAt())
                    .build());
        }
        return responses;
    }

    private List<OrderItemDetailResponse> mapDetailItems(List<OrderItemEntity> orderItems) {
        List<OrderItemDetailResponse> detailItems = new ArrayList<>();
        for (OrderItemEntity orderItem : orderItems) {
            detailItems.add(OrderItemDetailResponse.builder()
                    .productId(orderItem.getProductId())
                    .optionId(orderItem.getOptionId())
                    .productName(orderItem.getProductNameSnapshot())
                    .optionName(orderItem.getOptionNameSnapshot())
                    .unitPrice(orderItem.getUnitPrice())
                    .quantity(orderItem.getQuantity())
                    .lineAmount(orderItem.getLineAmount())
                    .status(orderItem.getStatus().name())
                    .build());
        }
        return detailItems;
    }
}
