package com.wearhouse.order.buyer.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.order.buyer.dto.request.OrderCreateRequest;
import com.wearhouse.order.buyer.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.buyer.dto.response.OrderCreateResponse;
import com.wearhouse.order.common.event.OrderEventType;
import com.wearhouse.order.common.exception.OrderErrorCode;
import com.wearhouse.order.common.util.OrderIdGenerator;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderSagaEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import com.wearhouse.order.support.monitoring.OrderFlowMetrics;
import com.wearhouse.order.support.monitoring.OrderInventoryReservationMetrics;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class BuyerOrderCreateOrchestrationService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final Long DEFAULT_SELLER_ID = 1L;

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final OrderKafkaTopicsProperties kafkaTopicsProperties;
    private final TransactionTemplate transactionTemplate;
    private final OrderInventoryReservationMetrics orderInventoryReservationMetrics;
    private final OrderFlowMetrics orderFlowMetrics;

    public OrderCreateResponse createOrder(Long authenticatedBuyerId, OrderCreateRequest request) {
        OrderCreateRequest normalizedRequest = normalizeRequest(authenticatedBuyerId, request);
        OrderCreateContext context = transactionTemplate.execute(status -> {
            OrderCreateContext txContext = prepareCreateContext(normalizedRequest);
            OrderEntity order = createOrderEntity(
                    normalizedRequest,
                    txContext.orderNo(),
                    txContext.orderedAt(),
                    txContext.amountSummary()
            );
            List<ReservePayloadItem> payloadItems = appendItemsAndBuildReservePayload(order, normalizedRequest.items());
            saveCreatedOrder(order);
            startSaga(order, txContext.eventId());
            publishInventoryReserveRequested(order, normalizedRequest, txContext, payloadItems);
            return txContext;
        });

        if (context == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "주문 생성 결과가 비어 있습니다.");
        }

        OrderCreateResponse.OrderCreateResponseBuilder responseBuilder = OrderCreateResponse.builder()
                .orderNo(context.orderNo())
                .customerId(context.customerId())
                .customerName(normalizedRequest.recipientName())
                .payAmount(context.amountSummary().payAmount());

        return responseBuilder.build();
    }

    private OrderAmountSummary calculateAmountSummary(OrderCreateRequest request) {
        BigDecimal itemAmount = calculateItemAmount(request.items());
        BigDecimal shippingFee = safe(request.shippingFee());
        BigDecimal discountAmount = safe(request.discountAmount());
        BigDecimal pointUsedAmount = safe(request.pointUsedAmount());
        BigDecimal payAmount = itemAmount.add(shippingFee).subtract(discountAmount).subtract(pointUsedAmount);

        if (payAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        return new OrderAmountSummary(itemAmount, shippingFee, discountAmount, pointUsedAmount, payAmount);
    }

    private OrderEntity createOrderEntity(
            OrderCreateRequest request,
            String orderNo,
            LocalDateTime orderedAt,
            OrderAmountSummary amountSummary
    ) {
        OrderInfo orderInfo = OrderInfo.of(
                request.paymentMethod(),
                request.recipientName(),
                request.recipientPhone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                request.deliveryRequest()
        );

        return OrderEntity.of(
                orderNo,
                request.buyerId(),
                OrderStatus.PENDING_RESERVE,
                amountSummary.payAmount(),
                "KRW",
                amountSummary.itemAmount(),
                amountSummary.shippingFee(),
                amountSummary.discountAmount(),
                amountSummary.pointUsedAmount(),
                orderInfo,
                orderedAt
        );
    }

    private List<ReservePayloadItem> appendItemsAndBuildReservePayload(
            OrderEntity order,
            List<OrderCreateItemRequest> requestItems
    ) {
        List<ReservePayloadItem> payloadItems = new ArrayList<>(requestItems.size());
        for (OrderCreateItemRequest requestItem : requestItems) {
            BigDecimal lineAmount = requestItem.unitPrice().multiply(BigDecimal.valueOf(requestItem.quantity()));
            order.addItem(
                    requestItem.productId(),
                    requestItem.optionId(),
                    resolveSellerId(requestItem),
                    requestItem.productName(),
                    requestItem.optionName(),
                    requestItem.unitPrice(),
                    requestItem.quantity(),
                    lineAmount
            );
            payloadItems.add(toReservePayloadItem(requestItem));
        }
        return payloadItems;
    }

    private Long resolveSellerId(OrderCreateItemRequest requestItem) {
        return DEFAULT_SELLER_ID;
    }

    private ReservePayloadItem toReservePayloadItem(OrderCreateItemRequest requestItem) {
        return new ReservePayloadItem(
                requestItem.productId(),
                requestItem.optionId(),
                requestItem.quantity()
        );
    }

    private void saveCreatedOrder(OrderEntity order) {
        orderRepository.save(order);
    }

    private void startSaga(OrderEntity order, String eventId) {
        String sagaId = OrderIdGenerator.newSagaId();
        OrderSagaEntity saga = OrderSagaEntity.of(
                order,
                sagaId,
                OrderSagaState.WAITING_INVENTORY,
                eventId
        );
        orderSagaRepository.save(saga);
        orderFlowMetrics.recordSagaTransition(
                null,
                OrderSagaState.WAITING_INVENTORY,
                "ORDER_CREATED"
        );
    }

    private void publishInventoryReserveRequested(
            OrderEntity order,
            OrderCreateRequest request,
            OrderCreateContext context,
            List<ReservePayloadItem> payloadItems
    ) {
        InventoryReserveRequestedPayload payload = buildInventoryReservePayload(order, request, context, payloadItems);
        publishDomainEvent(
                context.eventId(),
                OrderEventType.INVENTORY_RESERVE_REQUESTED,
                order.getId(),
                kafkaTopicsProperties.inventoryReserveTopic(),
                payload
        );
        orderInventoryReservationMetrics.recordReserveRequested();
    }

    private InventoryReserveRequestedPayload buildInventoryReservePayload(
            OrderEntity order,
            OrderCreateRequest request,
            OrderCreateContext context,
            List<ReservePayloadItem> payloadItems
    ) {
        return new InventoryReserveRequestedPayload(
                order.getId(),
                order.getOrderNo(),
                request.buyerId(),
                context.amountSummary().payAmount(),
                request.paymentMethod() == null ? null : request.paymentMethod().name(),
                request.recipientName(),
                request.recipientPhone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                context.orderedAt(),
                payloadItems
        );
    }

    private OrderCreateContext prepareCreateContext(OrderCreateRequest request) {
        validateCreateRequest(request);

        OrderAmountSummary amountSummary = calculateAmountSummary(request);
        String orderNo = OrderIdGenerator.newOrderNo();
        LocalDateTime orderedAt = LocalDateTime.now();
        String eventId = OrderIdGenerator.newEventId();
        String customerKey = resolveCustomerKey(request.buyerId(), orderNo);
        String customerId = UUID.nameUUIDFromBytes(customerKey.getBytes(StandardCharsets.UTF_8)).toString();

        return new OrderCreateContext(orderNo, orderedAt, eventId, customerId, amountSummary);
    }

    private OrderCreateRequest normalizeRequest(Long authenticatedBuyerId, OrderCreateRequest request) {
        if (request == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        return new OrderCreateRequest(
                authenticatedBuyerId,
                request.paymentMethod(),
                request.recipientName(),
                request.recipientPhone(),
                request.zipCode(),
                request.address1(),
                request.address2(),
                request.deliveryRequest(),
                request.shippingFee(),
                request.discountAmount(),
                request.pointUsedAmount(),
                request.items()
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

    private void validateCreateRequest(OrderCreateRequest request) {
        if (request == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (request.paymentMethod() == null) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new ErrorException(OrderErrorCode.ORDER_ITEM_EMPTY);
        }

        for (OrderCreateItemRequest item : request.items()) {
            if (item.productId() == null
                    || item.unitPrice() == null
                    || item.quantity() == null
                    || item.quantity() <= 0
                    || item.unitPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT);
            }
        }
    }

    private String resolveCustomerKey(Long buyerId, String orderNo) {
        if (buyerId != null) {
            return "buyer:" + buyerId;
        }
        return "guest:" + orderNo;
    }

    private BigDecimal calculateItemAmount(List<OrderCreateItemRequest> items) {
        BigDecimal total = BigDecimal.ZERO;
        for (OrderCreateItemRequest item : items) {
            total = total.add(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        }
        return total;
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private record ReservePayloadItem(
            Long productId,
            Long optionId,
            Integer quantity
    ) {
    }

    private record InventoryReserveRequestedPayload(
            Long orderId,
            String orderNo,
            Long buyerId,
            BigDecimal payAmount,
            String paymentMethod,
            String recipientName,
            String recipientPhone,
            String zipCode,
            String address1,
            String address2,
            LocalDateTime orderedAt,
            List<ReservePayloadItem> items
    ) {
    }

    private record OrderAmountSummary(
            BigDecimal itemAmount,
            BigDecimal shippingFee,
            BigDecimal discountAmount,
            BigDecimal pointUsedAmount,
            BigDecimal payAmount
    ) {
    }

    private record OrderCreateContext(
            String orderNo,
            LocalDateTime orderedAt,
            String eventId,
            String customerId,
            OrderAmountSummary amountSummary
    ) {
    }
}
