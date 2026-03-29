package com.wearhouse.order.delivery.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.delivery.dto.request.DeliveryDeliveredRequest;
import com.wearhouse.order.delivery.dto.request.DeliveryRegisterRequest;
import com.wearhouse.order.delivery.dto.request.DeliveryRegisterRequest.DeliveryRegisterItemRequest;
import com.wearhouse.order.delivery.dto.response.DeliveryBatchUpdateResponse;
import com.wearhouse.order.domain.entity.DeliveryEntity;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.DeliveryStatus;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.DeliveryRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.common.util.OrderIdGenerator;
import com.wearhouse.order.support.config.OrderProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DeliveryCommandService {

    private static final Set<OrderStatus> REGISTERABLE_ORDER_STATUSES = Set.of(
            OrderStatus.CONFIRMED
    );
    private static final Set<DeliveryStatus> BLOCK_CANCEL_DELIVERY_STATUSES = Set.of(
            DeliveryStatus.IN_DELIVERY,
            DeliveryStatus.DELIVERED
    );
    private static final String REASON_DELIVERY_MARKED_DELIVERED = "SELLER_DELIVERY_MARKED_DELIVERED";
    private static final String REASON_AUTO_PURCHASE_CONFIRMED = "AUTO_PURCHASE_CONFIRMED_D_PLUS_7";
    private static final int AUTO_CONFIRM_BATCH_SIZE = 100;

    private final OrderRepository orderRepository;
    private final DeliveryRepository deliveryRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderProperties orderProperties;

    @WriteTx
    public DeliveryBatchUpdateResponse registerDeliveries(LoginUser currentUser, DeliveryRegisterRequest request) {
        LinkedHashSet<Long> orderIds = extractOrderIds(request.items());
        Map<Long, OrderEntity> orders = loadOrders(orderIds);
        Map<Long, DeliveryEntity> deliveriesByOrderId = loadDeliveries(orderIds);

        List<Long> processedOrderIds = new ArrayList<>(orderIds.size());
        for (DeliveryRegisterItemRequest item : request.items()) {
            OrderEntity order = requireOrder(orders, item.orderId());
            validateRegisterableOrderStatus(order.getStatus());

            DeliveryEntity delivery = deliveriesByOrderId.get(item.orderId());
            if (delivery == null) {
                delivery = deliveryRepository.save(DeliveryEntity.create(
                        order,
                        normalize(item.courierCode()),
                        normalize(item.invoiceNo()),
                        DeliveryStatus.IN_DELIVERY
                ));
                deliveriesByOrderId.put(item.orderId(), delivery);
            } else {
                delivery.updateShipment(normalize(item.courierCode()), normalize(item.invoiceNo()));
                delivery.updateStatus(DeliveryStatus.IN_DELIVERY);
            }
            processedOrderIds.add(item.orderId());
        }

        return new DeliveryBatchUpdateResponse(processedOrderIds.size(), processedOrderIds);
    }

    @WriteTx
    public DeliveryBatchUpdateResponse markDelivered(LoginUser currentUser, DeliveryDeliveredRequest request) {
        LinkedHashSet<Long> orderIds = new LinkedHashSet<>(request.orderIds());
        Map<Long, OrderEntity> orders = loadOrders(orderIds);
        Map<Long, DeliveryEntity> deliveriesByOrderId = loadDeliveries(orderIds);

        List<Long> processedOrderIds = new ArrayList<>(orderIds.size());
        for (Long orderId : orderIds) {
            OrderEntity order = requireOrder(orders, orderId);

            DeliveryEntity delivery = deliveriesByOrderId.get(orderId);
            if (delivery == null) {
                throw new ErrorException(OrderErrorCode.DELIVERY_NOT_FOUND);
            }
            if (delivery.getStatus() != DeliveryStatus.DELIVERED) {
                delivery.updateStatus(DeliveryStatus.DELIVERED);
            }
            if (order.getStatus() != OrderStatus.DELIVERED) {
                OrderStatus fromStatus = order.getStatus();
                order.updateStatus(OrderStatus.DELIVERED, null, null, null);
                saveStatusHistory(order, fromStatus, OrderStatus.DELIVERED, REASON_DELIVERY_MARKED_DELIVERED);
            }
            processedOrderIds.add(orderId);
        }

        return new DeliveryBatchUpdateResponse(processedOrderIds.size(), processedOrderIds);
    }

    @WriteTx
    public int autoConfirmDeliveredOrders() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(orderProperties.getDeliveryPurchaseConfirmDelayDays());
        List<DeliveryEntity> targets = deliveryRepository.findAutoConfirmTargets(
                DeliveryStatus.DELIVERED,
                threshold,
                OrderStatus.DELIVERED,
                PageRequest.of(0, AUTO_CONFIRM_BATCH_SIZE)
        );

        int processed = 0;
        for (DeliveryEntity delivery : targets) {
            OrderEntity order = delivery.getOrder();
            if (order.getStatus() != OrderStatus.DELIVERED) {
                continue;
            }
            order.updateStatus(OrderStatus.PURCHASE_CONFIRMED, null, null, null);
            saveStatusHistory(order, OrderStatus.DELIVERED, OrderStatus.PURCHASE_CONFIRMED, REASON_AUTO_PURCHASE_CONFIRMED);
            processed++;
        }
        return processed;
    }

    public boolean isCancelBlockedByDelivery(Long orderId) {
        return deliveryRepository.existsByOrder_IdAndStatusIn(orderId, BLOCK_CANCEL_DELIVERY_STATUSES);
    }

    private LinkedHashSet<Long> extractOrderIds(List<DeliveryRegisterItemRequest> items) {
        LinkedHashSet<Long> orderIds = new LinkedHashSet<>();
        for (DeliveryRegisterItemRequest item : items) {
            if (!orderIds.add(item.orderId())) {
                throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "중복된 orderId가 포함되어 있습니다.");
            }
        }
        return orderIds;
    }

    private Map<Long, OrderEntity> loadOrders(Set<Long> orderIds) {
        List<OrderEntity> orders = orderRepository.findDetailsByIdIn(orderIds);
        Map<Long, OrderEntity> map = new LinkedHashMap<>();
        for (OrderEntity order : orders) {
            map.put(order.getId(), order);
        }
        return map;
    }

    private Map<Long, DeliveryEntity> loadDeliveries(Set<Long> orderIds) {
        List<DeliveryEntity> deliveries = deliveryRepository.findByOrder_IdIn(orderIds);
        Map<Long, DeliveryEntity> map = new LinkedHashMap<>();
        for (DeliveryEntity delivery : deliveries) {
            map.put(delivery.getOrder().getId(), delivery);
        }
        return map;
    }

    private OrderEntity requireOrder(Map<Long, OrderEntity> orders, Long orderId) {
        OrderEntity order = orders.get(orderId);
        if (order == null) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    private void validateRegisterableOrderStatus(OrderStatus status) {
        if (!REGISTERABLE_ORDER_STATUSES.contains(status)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "배송 등록 가능한 주문 상태가 아닙니다.");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private void saveStatusHistory(
            OrderEntity order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String reasonCode
    ) {
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                fromStatus,
                toStatus,
                OrderIdGenerator.newEventId(),
                reasonCode
        ));
    }
}
