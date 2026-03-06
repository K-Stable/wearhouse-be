package com.wearhouse.order.domain.order.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.domain.order.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.order.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.order.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.domain.order.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.order.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.order.entity.OrderEntity;
import com.wearhouse.order.domain.order.entity.OrderInfo;
import com.wearhouse.order.domain.order.entity.OrderSagaEntity;
import com.wearhouse.order.domain.order.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.order.event.OrderDomainEvent;
import com.wearhouse.order.domain.order.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.order.exception.OrderErrorCode;
import com.wearhouse.order.domain.order.model.OrderSagaState;
import com.wearhouse.order.domain.order.model.OrderStatus;
import com.wearhouse.order.domain.order.repository.OrderRepository;
import com.wearhouse.order.domain.order.repository.OrderSagaRepository;
import com.wearhouse.order.domain.order.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.domain.order.support.OrderIdGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCommandService {

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

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final String inventoryReserveTopic;
    private final String inventoryCommandTopic;
    private final int paymentResultTimeoutMinutes;

    public OrderCommandService(
            OrderRepository orderRepository,
            OrderSagaRepository orderSagaRepository,
            OrderStatusHistoryRepository orderStatusHistoryRepository,
            OrderDomainEventPublisher orderDomainEventPublisher,
            @Value("${wearhouse.kafka.inventory-reserve-topic:wearhouse.inventory.command.v1}") String inventoryReserveTopic,
            @Value("${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}") String inventoryCommandTopic,
            @Value("${wearhouse.order.payment-result-timeout-minutes:30}") int paymentResultTimeoutMinutes
    ) {
        this.orderRepository = orderRepository;
        this.orderSagaRepository = orderSagaRepository;
        this.orderStatusHistoryRepository = orderStatusHistoryRepository;
        this.orderDomainEventPublisher = orderDomainEventPublisher;
        this.inventoryReserveTopic = inventoryReserveTopic;
        this.inventoryCommandTopic = inventoryCommandTopic;
        this.paymentResultTimeoutMinutes = paymentResultTimeoutMinutes;
    }

    @Transactional
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        validateCreateRequest(request);

        BigDecimal itemAmount = calculateItemAmount(request.getItems());
        BigDecimal shippingFee = safe(request.getShippingFee());
        BigDecimal discountAmount = safe(request.getDiscountAmount());
        BigDecimal pointUsedAmount = safe(request.getPointUsedAmount());
        BigDecimal payAmount = itemAmount.add(shippingFee).subtract(discountAmount).subtract(pointUsedAmount);
        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }

        String orderNo = OrderIdGenerator.newOrderNo();
        String sagaId = OrderIdGenerator.newSagaId();
        String eventId = OrderIdGenerator.newEventId();
        LocalDateTime orderedAt = LocalDateTime.now();

        OrderInfo orderInfo = OrderInfo.of(
                request.getPaymentMethod(),
                request.getRecipientName(),
                request.getRecipientPhone(),
                request.getZipCode(),
                request.getAddress1(),
                request.getAddress2(),
                request.getDeliveryRequest()
        );

        OrderEntity order = OrderEntity.create(
                orderNo,
                request.getBuyerId(),
                OrderStatus.PENDING_RESERVE,
                payAmount,
                "KRW",
                itemAmount,
                shippingFee,
                discountAmount,
                pointUsedAmount,
                orderInfo,
                orderedAt
        );

        List<Map<String, Object>> payloadItems = new ArrayList<>();
        for (OrderCreateItemRequest requestItem : request.getItems()) {
            BigDecimal lineAmount = requestItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(requestItem.getQuantity()));

            order.addItem(
                    requestItem.getProductId(),
                    requestItem.getOptionId(),
                    requestItem.getSellerId(),
                    requestItem.getProductName(),
                    requestItem.getOptionName(),
                    requestItem.getUnitPrice(),
                    requestItem.getQuantity(),
                    lineAmount
            );

            Map<String, Object> payloadItem = new LinkedHashMap<>();
            payloadItem.put("productId", requestItem.getProductId());
            payloadItem.put("optionId", requestItem.getOptionId());
            payloadItem.put("sellerId", requestItem.getSellerId());
            payloadItem.put("quantity", requestItem.getQuantity());
            payloadItems.add(payloadItem);
        }

        orderRepository.save(order);

        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                null,
                OrderStatus.PENDING_RESERVE,
                eventId,
                "ORDER_CREATED"
        ));

        OrderSagaEntity saga = OrderSagaEntity.create(
                order,
                sagaId,
                OrderSagaState.WAITING_INVENTORY,
                eventId,
                "OrderCreated",
                orderedAt.plusMinutes(paymentResultTimeoutMinutes)
        );
        orderSagaRepository.save(saga);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", orderNo);
        payload.put("buyerId", request.getBuyerId());
        payload.put("payAmount", payAmount);
        payload.put("paymentMethod", request.getPaymentMethod());
        payload.put("recipientName", request.getRecipientName());
        payload.put("recipientPhone", request.getRecipientPhone());
        payload.put("zipCode", request.getZipCode());
        payload.put("address1", request.getAddress1());
        payload.put("address2", request.getAddress2());
        payload.put("items", payloadItems);

        OrderDomainEvent event = OrderDomainEvent.builder()
                .eventId(eventId)
                .eventType("InventoryReserveRequested")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(order.getId()))
                .topic(inventoryReserveTopic)
                .partitionKey(String.valueOf(order.getId()))
                .payload(payload)
                .build();
        orderDomainEventPublisher.publish(event);

        return OrderCreateResponse.builder()
                .orderId(order.getId())
                .orderNo(orderNo)
                .status(OrderStatus.PENDING_RESERVE.name())
                .payAmount(payAmount)
                .sagaId(sagaId)
                .outboxEventId(eventId)
                .orderedAt(orderedAt)
                .build();
    }

    @Transactional
    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderStatus currentStatus = order.getStatus();

        if (!CANCELLABLE_STATUSES.contains(currentStatus)) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE);
        }

        String cancelReasonCode = request == null || isBlank(request.getReasonCode())
                ? "BUYER_CANCEL"
                : request.getReasonCode();
        LocalDateTime cancelledAt = LocalDateTime.now();
        String cancelEventId = OrderIdGenerator.newEventId();

        order.updateStatus(OrderStatus.CANCELLED, cancelReasonCode, null, cancelledAt);
        order.markItemsCancelled();

        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
                order,
                currentStatus,
                OrderStatus.CANCELLED,
                cancelEventId,
                cancelReasonCode
        ));

        orderSagaRepository.findByOrder_Id(order.getId())
                .ifPresent(saga -> saga.transition(
                        OrderSagaState.CANCELLED,
                        cancelEventId,
                        "OrderCancelled",
                        cancelReasonCode
                ));

        if (RELEASE_REQUIRED_STATUSES.contains(currentStatus)) {
            String releaseEventId = OrderIdGenerator.newEventId();
            Map<String, Object> releasePayload = new LinkedHashMap<>();
            releasePayload.put("orderId", order.getId());
            releasePayload.put("orderNo", orderNo);
            releasePayload.put("reasonCode", cancelReasonCode);

            OrderDomainEvent releaseEvent = OrderDomainEvent.builder()
                    .eventId(releaseEventId)
                    .eventType("InventoryReleaseRequested")
                    .aggregateType("ORDER")
                    .aggregateId(String.valueOf(order.getId()))
                    .topic(inventoryCommandTopic)
                    .partitionKey(String.valueOf(order.getId()))
                    .payload(releasePayload)
                    .build();
            orderDomainEventPublisher.publish(releaseEvent);
        }

        return OrderCancelResponse.builder()
                .orderNo(orderNo)
                .status(OrderStatus.CANCELLED.name())
                .reasonCode(cancelReasonCode)
                .cancelledAt(cancelledAt)
                .build();
    }

    private void validateCreateRequest(OrderCreateRequest request) {
        if (request == null || request.getBuyerId() == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
        }

        for (OrderCreateItemRequest item : request.getItems()) {
            if (item.getProductId() == null
                    || item.getSellerId() == null
                    || item.getUnitPrice() == null
                    || item.getQuantity() == null
                    || item.getQuantity() <= 0
                    || item.getUnitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
            }
        }
    }

    private BigDecimal calculateItemAmount(List<OrderCreateItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderCreateItemRequest item : items) {
            total = total.add(item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())));
        }
        return total;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
