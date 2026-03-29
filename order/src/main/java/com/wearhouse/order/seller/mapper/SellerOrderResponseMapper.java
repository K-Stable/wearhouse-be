package com.wearhouse.order.seller.mapper;

import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderItemEntity;
import com.wearhouse.order.seller.dto.response.SellerOrderListItemResponse;
import com.wearhouse.order.seller.dto.response.SellerOrderListPageResponse;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

@Component
public class SellerOrderResponseMapper {

    public SellerOrderListPageResponse toPageResponse(Page<OrderEntity> orders, List<SellerOrderListItemResponse> content) {
        return new SellerOrderListPageResponse(
                content,
                orders.getNumber(),
                orders.getSize(),
                orders.getTotalElements(),
                orders.getTotalPages(),
                orders.hasNext(),
                orders.hasPrevious()
        );
    }

    public SellerOrderListItemResponse toListItem(OrderEntity order) {
        int totalQuantity = order.getItems().stream()
                .mapToInt(OrderItemEntity::getQuantity)
                .sum();
        OrderInfo info = order.getOrderInfo();
        return new SellerOrderListItemResponse(
                order.getOrderNo(),
                order.getBuyerId(),
                totalQuantity,
                info == null || info.getPaymentMethod() == null ? null : info.getPaymentMethod().name(),
                order.getOrderedAt() == null ? null : order.getOrderedAt().toLocalDate(),
                order.getStatus() == null ? null : order.getStatus().name()
        );
    }
}
