package com.wearhouse.order.domain.order.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.order.entity.OrderEntity;
import com.wearhouse.order.domain.order.entity.OrderSagaEntity;
import com.wearhouse.order.domain.order.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.order.event.OrderDomainEvent;
import com.wearhouse.order.domain.order.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.order.exception.OrderErrorCode;
import com.wearhouse.order.domain.order.model.OrderSagaState;
import com.wearhouse.order.domain.order.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderInboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.support.OrderIdGenerator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderSagaCommandService {

    private static final Set<OrderStatus> PAYMENT_RESULT_WAITING_STATUSES = Set.of(
            OrderStatus.PAYMENT_PENDING,
            OrderStatus.RESERVED
    );

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderInboxRepository orderInboxRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final String paymentPrepareTopic;
    private final String inventoryCommandTopic;
    private final String orderEventTopic;

    public OrderSagaCommandService(
            OrderRepository orderRepository,
            OrderSagaRepository orderSagaRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            OrderInboxRepository orderInboxRepository,
            OrderDomainEventPublisher orderDomainEventPublisher,
            @Value("${wearhouse.kafka.payment-prepare-topic:wearhouse.payment.command.v1}") String paymentPrepareTopic,
            @Value("${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}") String inventoryCommandTopic,
            @Value("${wearhouse.kafka.order-event-topic:wearhouse.order.event.v1}") String orderEventTopic
    ) {
        this.orderRepository = orderRepository;
        this.orderSagaRepository = orderSagaRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.orderInboxRepository = orderInboxRepository;
        this.orderDomainEventPublisher = orderDomainEventPublisher;
        this.paymentPrepareTopic = paymentPrepareTopic;
        this.inventoryCommandTopic = inventoryCommandTopic;
        this.orderEventTopic = orderEventTopic;
    }

    @Transactional
    public void handleInventoryEvent(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Map<String, Object> payload
    ) {
        boolean received = orderInboxRepository.tryReceive(
                eventId,
                "order-inventory-consumer",
                eventType,
                topic,
                partitionKey,
                rawPayload
        );
        if (!received) {
            return;
        }

        try {
            Long orderId = asLong(payload.get("orderId"));
            OrderEntity order = orderRepository.findDetailById(orderId)
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
            OrderStatus currentStatus = order.getStatus();

            if ("StockReserved".equals(eventType)) {
                if (currentStatus == OrderStatus.PENDING_RESERVE) {
                    order.updateStatus(OrderStatus.RESERVED, null, null, null);
                    order.markItemsReserved();
                    orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                            order,
                            currentStatus,
                            OrderStatus.RESERVED,
                            eventId,
                            "STOCK_RESERVED"
                    ));
                    transitionSaga(order.getId(), OrderSagaState.WAITING_PAYMENT_PREPARE, eventId, eventType, null);
                    publishPaymentPrepareRequested(order);
                }
            } else if ("StockReserveFailed".equals(eventType)) {
                String reasonCode = asString(payload.get("reasonCode"), "STOCK_RESERVE_FAILED");
                if (currentStatus == OrderStatus.PENDING_RESERVE) {
                    order.updateStatus(OrderStatus.RESERVE_FAILED, reasonCode, null, null);
                    orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                            order,
                            currentStatus,
                            OrderStatus.RESERVE_FAILED,
                            eventId,
                            reasonCode
                    ));
                    transitionSaga(order.getId(), OrderSagaState.RESERVE_FAILED, eventId, eventType, reasonCode);
                }
            } else if ("InventoryReleased".equals(eventType)) {
                if (currentStatus == OrderStatus.PAYMENT_FAILED) {
                    order.updateStatus(OrderStatus.CANCELLED, "PAYMENT_FAILED", null, LocalDateTime.now());
                    order.markItemsCancelled();
                    orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                            order,
                            currentStatus,
                            OrderStatus.CANCELLED,
                            eventId,
                            "INVENTORY_RELEASED"
                    ));
                    transitionSaga(order.getId(), OrderSagaState.CANCELLED, eventId, eventType, null);
                    publishOrderCancelled(order);
                }
            }

            orderInboxRepository.markProcessed(eventId, "order-inventory-consumer");
        } catch (Exception exception) {
            orderInboxRepository.markFailed(eventId, "order-inventory-consumer", "CONSUME_FAIL", exception.getMessage());
            throw exception;
        }
    }

    @Transactional
    public void handlePaymentEvent(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String rawPayload,
            Map<String, Object> payload
    ) {
        boolean received = orderInboxRepository.tryReceive(
                eventId,
                "order-payment-consumer",
                eventType,
                topic,
                partitionKey,
                rawPayload
        );
        if (!received) {
            return;
        }

        try {
            Long orderId = asLong(payload.get("orderId"));
            OrderEntity order = orderRepository.findDetailById(orderId)
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
            OrderStatus currentStatus = order.getStatus();

            if ("PaymentAuthorized".equals(eventType)) {
                if (PAYMENT_RESULT_WAITING_STATUSES.contains(currentStatus)) {
                    order.updateStatus(OrderStatus.PAID, null, null, null);
                    orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                            order,
                            currentStatus,
                            OrderStatus.PAID,
                            eventId,
                            "PAYMENT_AUTHORIZED"
                    ));

                    order.updateStatus(OrderStatus.CONFIRMED, null, LocalDateTime.now(), null);
                    order.markItemsConfirmed();
                    orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                            order,
                            OrderStatus.PAID,
                            OrderStatus.CONFIRMED,
                            eventId,
                            "ORDER_CONFIRMED"
                    ));
                    transitionSaga(order.getId(), OrderSagaState.CONFIRMED, eventId, eventType, null);
                    publishOrderConfirmed(order);
                }
            } else if ("PaymentFailed".equals(eventType)) {
                String reasonCode = asString(payload.get("reasonCode"), "PAYMENT_FAILED");
                if (PAYMENT_RESULT_WAITING_STATUSES.contains(currentStatus)) {
                    order.updateStatus(OrderStatus.PAYMENT_FAILED, reasonCode, null, null);
                    orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                            order,
                            currentStatus,
                            OrderStatus.PAYMENT_FAILED,
                            eventId,
                            reasonCode
                    ));
                    transitionSaga(order.getId(), OrderSagaState.COMPENSATING, eventId, eventType, reasonCode);
                    publishInventoryReleaseRequested(order, reasonCode);
                }
            }

            orderInboxRepository.markProcessed(eventId, "order-payment-consumer");
        } catch (Exception exception) {
            orderInboxRepository.markFailed(eventId, "order-payment-consumer", "CONSUME_FAIL", exception.getMessage());
            throw exception;
        }
    }

    private void transitionSaga(
            Long orderId,
            OrderSagaState nextState,
            String eventId,
            String eventType,
            String failReasonCode
    ) {
        orderSagaRepository.findByOrder_Id(orderId)
                .ifPresent(saga -> saga.transition(nextState, eventId, eventType, failReasonCode));
    }

    private void publishPaymentPrepareRequested(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", order.getBuyerId());
        payload.put("amount", order.getTotalAmount());
        payload.put("paymentMethod", order.getOrderInfo() == null ? null : order.getOrderInfo().getPaymentMethod());

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType("PaymentPrepareRequested")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(paymentPrepareTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);

        order.updateStatus(OrderStatus.PAYMENT_PENDING, null, null, null);
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                OrderStatus.RESERVED,
                OrderStatus.PAYMENT_PENDING,
                eventId,
                "PAYMENT_PREPARE_REQUESTED"
        ));
        transitionSaga(order.getId(), OrderSagaState.WAITING_PAYMENT_RESULT, eventId, "PaymentPrepareRequested", null);
    }

    private void publishInventoryReleaseRequested(OrderEntity order, String reasonCode) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("reasonCode", reasonCode);

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType("InventoryReleaseRequested")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(inventoryCommandTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private void publishOrderConfirmed(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", order.getBuyerId());
        payload.put("confirmedAt", LocalDateTime.now());

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType("OrderConfirmed")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(orderEventTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private void publishOrderCancelled(OrderEntity order) {
        String eventId = OrderIdGenerator.newEventId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", order.getBuyerId());
        payload.put("cancelledAt", LocalDateTime.now());

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType("OrderCancelled")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(orderEventTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        throw new IllegalArgumentException("orderId 값이 올바르지 않습니다.");
    }

    private String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
