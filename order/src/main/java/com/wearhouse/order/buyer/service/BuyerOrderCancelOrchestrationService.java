package com.wearhouse.order.buyer.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.buyer.dto.request.OrderCancelRequest;
import com.wearhouse.order.buyer.dto.response.OrderCancelResponse;
import com.wearhouse.order.common.event.OrderEventType;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.common.util.OrderIdGenerator;
import com.wearhouse.order.delivery.service.DeliveryValidationService;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerOrderCancelOrchestrationService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String DEFAULT_CANCEL_REASON = "BUYER_CANCEL";

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final OrderKafkaTopicsProperties kafkaTopicsProperties;
    private final DeliveryValidationService deliveryValidationService;

    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderStatus currentStatus = order.getStatus();

        validateNotInDelivery(order.getId());
        validateCancellableStatus(currentStatus);

        String cancelReasonCode = resolveCancelReason(request);
        LocalDateTime cancelledAt = LocalDateTime.now();
        String cancelEventId = OrderIdGenerator.newEventId();

        applyOrderCancellation(order, currentStatus, cancelReasonCode, cancelledAt, cancelEventId);
        publishInventoryReleaseIfRequired(order, currentStatus, cancelReasonCode);

        return OrderCancelResponse.builder()
                .orderNo(orderNo)
                .status(OrderStatus.CANCELLED.name())
                .reasonCode(cancelReasonCode)
                .cancelledAt(cancelledAt)
                .build();
    }

    private String resolveCancelReason(OrderCancelRequest request) {
        if (request == null || isBlank(request.reasonCode())) {
            return DEFAULT_CANCEL_REASON;
        }
        return request.reasonCode();
    }

    private void validateCancellableStatus(OrderStatus currentStatus) {
        if (!OrderStatusPolicy.CANCELLABLE_STATUSES.contains(currentStatus)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE);
        }
    }

    private void validateNotInDelivery(Long orderId) {
        if (deliveryValidationService.isCancelBlockedByDelivery(orderId)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "배송이 시작된 주문은 취소할 수 없습니다.");
        }
    }

    private void applyOrderCancellation(
            OrderEntity order,
            OrderStatus currentStatus,
            String cancelReasonCode,
            LocalDateTime cancelledAt,
            String cancelEventId
    ) {
        order.updateStatus(OrderStatus.CANCELLED, cancelReasonCode, null, cancelledAt);
        order.markItemsCancelled();
        saveStatusHistory(order, currentStatus, OrderStatus.CANCELLED, cancelEventId, cancelReasonCode);
        transitionSaga(order.getId(), OrderSagaState.CANCELLED, cancelEventId, cancelReasonCode);
    }

    private void publishInventoryReleaseIfRequired(
            OrderEntity order,
            OrderStatus currentStatus,
            String cancelReasonCode
    ) {
        if (!OrderStatusPolicy.RELEASE_REQUIRED_STATUSES.contains(currentStatus)) {
            return;
        }

        String releaseEventId = OrderIdGenerator.newEventId();
        InventoryReleaseRequestedPayload releasePayload = new InventoryReleaseRequestedPayload(
                order.getId(),
                order.getOrderNo(),
                cancelReasonCode
        );

        publishDomainEvent(
                releaseEventId,
                OrderEventType.INVENTORY_RELEASE_REQUESTED,
                order.getId(),
                kafkaTopicsProperties.inventoryCommandTopic(),
                releasePayload
        );
    }

    private void publishDomainEvent(
            String eventId,
            String eventType,
            Long orderId,
            String topic,
            Object payload
    ) {
        String aggregateId = String.valueOf(orderId);
        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(eventType)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(aggregateId)
                .topic(topic)
                .partitionKey(aggregateId)
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private void saveStatusHistory(
            OrderEntity order,
            OrderStatus fromStatus,
            OrderStatus toStatus,
            String eventId,
            String reasonCode
    ) {
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.of(
                order,
                fromStatus,
                toStatus,
                eventId,
                reasonCode
        ));
    }

    private void transitionSaga(
            Long orderId,
            OrderSagaState nextState,
            String eventId,
            String failReasonCode
    ) {
        orderSagaRepository.findByOrder_Id(orderId)
                .ifPresent(saga -> saga.transition(nextState, eventId, failReasonCode));
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static final class OrderStatusPolicy {
        private static final Set<OrderStatus> CANCELLABLE_STATUSES = Set.of(
                OrderStatus.PENDING_RESERVE,
                OrderStatus.RESERVE_FAILED,
                OrderStatus.RESERVED,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_FAILED
        );

        private static final Set<OrderStatus> RELEASE_REQUIRED_STATUSES = Set.of(
                OrderStatus.RESERVED,
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_FAILED
        );
    }

    private record InventoryReleaseRequestedPayload(
            Long orderId,
            String orderNo,
            String reasonCode
    ) {
    }
}
