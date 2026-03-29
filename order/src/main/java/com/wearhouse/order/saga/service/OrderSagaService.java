package com.wearhouse.order.saga.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.common.event.OrderEventType;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.kafka.dto.InventoryEventPayload;
import com.wearhouse.order.kafka.dto.PaymentEventPayload;
import com.wearhouse.order.infra.jpa.repository.OrderInboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.common.util.OrderIdGenerator;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@RequiredArgsConstructor
@Service
public class OrderSagaService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String DEFAULT_PAYMENT_FAIL_REASON = "PAYMENT_FAILED";
    private static final String DEFAULT_STOCK_RESERVE_FAIL_REASON = "STOCK_RESERVE_FAILED";
    private static final String INVENTORY_CONSUMER = "order-inventory-consumer";
    private static final String PAYMENT_CONSUMER = "order-payment-consumer";

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderInboxRepository orderInboxRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final OrderKafkaTopicsProperties kafkaTopicsProperties;


    @WriteTx
    public void onInventoryEvent(
            String eventId,
            String eventType,
            InventoryEventPayload payload
    ) {
        processEvent(
                INVENTORY_CONSUMER,
                eventId,
                payload.orderId(),
                order -> dispatchInventoryEvent(order, eventType, eventId, payload)
        );
    }

    @WriteTx
    public void onPaymentEvent(
            String eventId,
            String eventType,
            PaymentEventPayload payload
    ) {
        processEvent(
                PAYMENT_CONSUMER,
                eventId,
                payload.orderId(),
                order -> dispatchPaymentEvent(order, eventType, eventId, payload)
        );
    }

    private void processEvent(
            String consumerName,
            String eventId,
            Long orderId,
            SagaEventProcessor processor
    ) {
        // Inbox로 중복 소비를 차단한다. (이미 처리된 eventId는 즉시 무시)
        if (!orderInboxRepository.tryReceive(eventId, consumerName)) {
            return;
        }

        try {
            OrderEntity order = loadOrder(orderId);
            processor.process(order);
            orderInboxRepository.markProcessed(eventId, consumerName);
        } catch (Exception exception) {
            orderInboxRepository.markFailed(eventId, consumerName);
            throw exception;
        }
    }

    private OrderEntity loadOrder(Long orderId) {
        if (orderId == null) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        return orderRepository.findDetailById(orderId)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void handleStockReserved(OrderEntity order, OrderStatus currentStatus, String eventId) {
        // 결제 실패 후 재고예약 이벤트가 지연 도착한 경우: 즉시 보상(해제)으로 수렴시킨다.
        if (currentStatus == OrderStatus.PAYMENT_FAILED) {
            order.markItemsReserved();
            publishInventoryReleaseRequested(order, resolveReasonCode(order.getFailReasonCode(), DEFAULT_PAYMENT_FAIL_REASON));
            transitionSaga(order.getId(), OrderSagaState.COMPENSATING, eventId, order.getFailReasonCode());
            return;
        }
        if (currentStatus != OrderStatus.PENDING_RESERVE) {
            return;
        }

        // 정상 경로: 예약 성공 -> RESERVED 전이 -> 결제 준비 이벤트 발행
        order.updateStatus(OrderStatus.RESERVED, null, null, null);
        order.markItemsReserved();
        saveStatusHistory(order, currentStatus, OrderStatus.RESERVED, eventId, "STOCK_RESERVED");
        transitionSaga(order.getId(), OrderSagaState.WAITING_PAYMENT_PREPARE, eventId, null);
        publishPaymentPrepareRequested(order);
    }

    private void handleStockReserveFailed(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            InventoryEventPayload payload
    ) {
        if (currentStatus != OrderStatus.PENDING_RESERVE) {
            return;
        }

        // 재고예약 실패는 주문을 RESERVE_FAILED로 종료한다.
        String reasonCode = resolveReasonCode(payload.reasonCode(), DEFAULT_STOCK_RESERVE_FAIL_REASON);
        order.updateStatus(OrderStatus.RESERVE_FAILED, reasonCode, null, null);
        saveStatusHistory(order, currentStatus, OrderStatus.RESERVE_FAILED, eventId, reasonCode);
        transitionSaga(order.getId(), OrderSagaState.RESERVE_FAILED, eventId, reasonCode);
    }

    private void handleInventoryReleased(OrderEntity order, OrderStatus currentStatus, String eventId) {
        if (currentStatus != OrderStatus.PAYMENT_FAILED) {
            return;
        }

        order.markItemsPendingReserve();
        transitionSaga(order.getId(), OrderSagaState.FAILED, eventId, order.getFailReasonCode());
    }

    private void handlePaymentAuthorized(OrderEntity order, OrderStatus currentStatus, String eventId) {
        if (currentStatus != OrderStatus.PAYMENT_PENDING && currentStatus != OrderStatus.RESERVED) {
            return;
        }

        // 결제 승인 성공 시 주문을 PAID -> CONFIRMED로 전이하고 후속 확정 이벤트를 발행한다.
        order.updateStatus(OrderStatus.PAID, null, null, null);
        saveStatusHistory(order, currentStatus, OrderStatus.PAID, eventId, "PAYMENT_AUTHORIZED");

        order.updateStatus(OrderStatus.CONFIRMED, null, LocalDateTime.now(), null);
        order.markItemsConfirmed();
        saveStatusHistory(order, OrderStatus.PAID, OrderStatus.CONFIRMED, eventId, "ORDER_CONFIRMED");
        transitionSaga(order.getId(), OrderSagaState.CONFIRMED, eventId, null);
        publishOrderConfirmed(order);
    }

    private void handlePaymentFailed(
            OrderEntity order,
            OrderStatus currentStatus,
            String eventId,
            PaymentEventPayload payload
    ) {
        if (currentStatus != OrderStatus.PENDING_RESERVE
                && currentStatus != OrderStatus.RESERVED
                && currentStatus != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        // 결제 실패 시 PAYMENT_FAILED 전이 후, 재고가 이미 예약된 주문은 보상 흐름으로 보낸다.
        String reasonCode = resolveReasonCode(payload.reasonCode(), DEFAULT_PAYMENT_FAIL_REASON);
        order.updateStatus(OrderStatus.PAYMENT_FAILED, reasonCode, null, null);
        saveStatusHistory(order, currentStatus, OrderStatus.PAYMENT_FAILED, eventId, reasonCode);

        if (currentStatus == OrderStatus.RESERVED || currentStatus == OrderStatus.PAYMENT_PENDING) {
            transitionSaga(order.getId(), OrderSagaState.COMPENSATING, eventId, reasonCode);
            publishInventoryReleaseRequested(order, reasonCode);
            return;
        }
        transitionSaga(order.getId(), OrderSagaState.FAILED, eventId, reasonCode);
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

    private void publishPaymentPrepareRequested(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        PaymentPrepareRequestedPayload payload = new PaymentPrepareRequestedPayload(
                order.getId(),
                order.getOrderNo(),
                order.getBuyerId(),
                order.getTotalAmount(),
                order.getOrderInfo() == null || order.getOrderInfo().getPaymentMethod() == null
                        ? null
                        : order.getOrderInfo().getPaymentMethod().name()
        );

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(OrderEventType.PAYMENT_PREPARE_REQUESTED)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(String.valueOf(order.getId()))
                .topic(kafkaTopicsProperties.paymentPrepareTopic())
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);

        // 주문 상태는 wallet prepare 응답 성공 시점(OrderPaymentIntegrationService)에서 PAYMENT_PENDING으로 전이한다.
    }

    private void publishInventoryReleaseRequested(OrderEntity order, String reasonCode) {
        String eventId = OrderIdGenerator.newEventId();
        InventoryReleaseRequestedPayload payload = new InventoryReleaseRequestedPayload(
                order.getId(),
                order.getOrderNo(),
                reasonCode
        );

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(OrderEventType.INVENTORY_RELEASE_REQUESTED)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(String.valueOf(order.getId()))
                .topic(kafkaTopicsProperties.inventoryCommandTopic())
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private void publishOrderConfirmed(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        OrderConfirmedPayload payload = new OrderConfirmedPayload(
                order.getId(),
                order.getOrderNo(),
                order.getBuyerId(),
                LocalDateTime.now()
        );

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType(OrderEventType.ORDER_CONFIRMED)
                .aggregateType(AGGREGATE_TYPE_ORDER)
                .aggregateId(String.valueOf(order.getId()))
                .topic(kafkaTopicsProperties.orderEventTopic())
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private String resolveReasonCode(String reasonCode, String defaultValue) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return defaultValue;
        }
        return reasonCode;
    }

    private void dispatchInventoryEvent(
            OrderEntity order,
            String eventType,
            String eventId,
            InventoryEventPayload payload
    ) {
        OrderStatus currentStatus = order.getStatus();
        switch (eventType) {
            case OrderEventType.STOCK_RESERVED -> handleStockReserved(order, currentStatus, eventId);
            case OrderEventType.STOCK_RESERVE_FAILED -> handleStockReserveFailed(order, currentStatus, eventId, payload);
            case OrderEventType.INVENTORY_RELEASED -> handleInventoryReleased(order, currentStatus, eventId);
            default -> {
                // no-op
            }
        }
    }

    private void dispatchPaymentEvent(
            OrderEntity order,
            String eventType,
            String eventId,
            PaymentEventPayload payload
    ) {
        OrderStatus currentStatus = order.getStatus();
        switch (eventType) {
            case OrderEventType.PAYMENT_AUTHORIZED -> handlePaymentAuthorized(order, currentStatus, eventId);
            case OrderEventType.PAYMENT_FAILED -> handlePaymentFailed(order, currentStatus, eventId, payload);
            default -> {
                // no-op
            }
        }
    }

    @FunctionalInterface
    private interface SagaEventProcessor {
        void process(OrderEntity order);
    }

    private record PaymentPrepareRequestedPayload(
            Long orderId,
            String orderNo,
            Long buyerId,
            java.math.BigDecimal amount,
            String paymentMethod
    ) {
    }

    private record InventoryReleaseRequestedPayload(
            Long orderId,
            String orderNo,
            String reasonCode
    ) {
    }

    private record OrderConfirmedPayload(
            Long orderId,
            String orderNo,
            Long buyerId,
            LocalDateTime confirmedAt
    ) {
    }
}
