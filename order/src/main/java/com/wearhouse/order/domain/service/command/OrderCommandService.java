package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.domain.dto.request.OrderCancelRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest.OrderCreateItemRequest;
import com.wearhouse.order.domain.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.domain.dto.response.OrderCancelResponse;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderSagaEntity;
import com.wearhouse.order.domain.entity.OrderStatusHistoryEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.event.OrderEventType;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.OrderSagaState;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalRequest;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalResponse;
import com.wearhouse.order.support.OrderIdGenerator;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import com.wearhouse.order.support.config.OrderProperties;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@Service
@RequiredArgsConstructor
public class OrderCommandService {

    private static final String AGGREGATE_TYPE_ORDER = "ORDER";
    private static final String REASON_ORDER_CREATED = "ORDER_CREATED";
    private static final String DEFAULT_CANCEL_REASON = "BUYER_CANCEL";

    private final OrderRepository orderRepository;
    private final OrderSagaRepository orderSagaRepository;
    private final OrderStatusHistoryRepository orderStatusHistoryRepository;
    private final OrderDomainEventPublisher orderDomainEventPublisher;
    private final OrderPaymentClient orderPaymentClient;
    private final OrderKafkaTopicsProperties kafkaTopicsProperties;
    private final OrderProperties orderProperties;

    @WriteTx
    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        OrderCreateContext context = prepareCreateContext(request);
        OrderEntity order = createOrderEntity(
                request,
                context.orderNo(),
                context.orderedAt(),
                context.amountSummary()
        );
        List<Map<String, Object>> payloadItems = appendItemsAndBuildReservePayload(order, request.items());

        saveCreatedOrder(order, context.eventId());
        startSaga(order, context.eventId());
        publishInventoryReserveRequested(order, request, context, payloadItems);

        return OrderCreateResponse.builder()
                .orderNo(context.orderNo())
                .customerId(context.customerId())
                .customerName(request.recipientName())
                .payAmount(context.amountSummary().payAmount())
                .build();
    }

    public OrderPaymentConfirmResponse confirmPayment(
            Long buyerId,
            String orderNo,
            OrderPaymentConfirmRequest request
    ) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        validateConfirmOrder(order, buyerId, request);

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            return new OrderPaymentConfirmResponse(order.getId(), order.getOrderNo(), OrderStatus.CONFIRMED.name(), null);
        }
        if (order.getStatus() == OrderStatus.PAYMENT_FAILED) {
            return new OrderPaymentConfirmResponse(
                    order.getId(),
                    order.getOrderNo(),
                    OrderStatus.PAYMENT_FAILED.name(),
                    order.getFailReasonCode()
            );
        }

        PaymentMethod paymentMethod = resolvePaymentMethod(order);
        if (paymentMethod != PaymentMethod.STABLE) {
            OrderStatus status = waitForConfirmResult(order.getId(), "PENDING");
            String reasonCode = orderRepository.findDetailById(order.getId()).map(OrderEntity::getFailReasonCode).orElse(null);
            return new OrderPaymentConfirmResponse(order.getId(), orderNo, status.name(), reasonCode);
        }
        validateStablePaymentConfirmRequest(request);

        ApiResponse<PaymentConfirmInternalResponse> confirmResponse = orderPaymentClient.confirmStablepayPayment(
                orderProperties.getInternal().getSharedSecret(),
                new PaymentConfirmInternalRequest(
                        request.orderId(),
                        orderNo,
                        request.paymentKey(),
                        request.amount()
                )
        );
        PaymentConfirmInternalResponse data = confirmResponse == null ? null : confirmResponse.data();
        String paymentStatus = data == null || data.paymentStatus() == null ? "" : data.paymentStatus();

        OrderStatus status = waitForConfirmResult(order.getId(), paymentStatus);
        String reasonCode = orderRepository.findDetailById(order.getId()).map(OrderEntity::getFailReasonCode).orElse(null);
        return new OrderPaymentConfirmResponse(order.getId(), orderNo, status.name(), reasonCode);
    }

    @WriteTx
    public OrderCancelResponse cancelOrder(String orderNo, OrderCancelRequest request) {
        OrderEntity order = orderRepository.findDetailByOrderNo(orderNo)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        OrderStatus currentStatus = order.getStatus();

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

    private OrderStatus waitForConfirmResult(Long orderId, String paymentStatus) {
        if ("PENDING".equalsIgnoreCase(paymentStatus)) {
            return orderRepository.findDetailById(orderId)
                    .map(OrderEntity::getStatus)
                    .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
        }

        long startedAt = System.currentTimeMillis();
        while ((System.currentTimeMillis() - startedAt) < orderProperties.getPaymentConfirmWaitTimeoutMs()) {
            Optional<OrderStatus> current = orderRepository.findDetailById(orderId).map(OrderEntity::getStatus);
            if (current.isEmpty()) {
                throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
            }
            OrderStatus currentStatus = current.get();
            if (currentStatus == OrderStatus.CONFIRMED || currentStatus == OrderStatus.PAYMENT_FAILED) {
                return currentStatus;
            }
            pause(orderProperties.getPaymentConfirmWaitIntervalMs());
        }
        return orderRepository.findDetailById(orderId)
                .map(OrderEntity::getStatus)
                .orElseThrow(() -> new ErrorException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void validateConfirmOrder(OrderEntity order, Long buyerId, OrderPaymentConfirmRequest request) {
        if (order.getBuyerId() == null || !order.getBuyerId().equals(buyerId)) {
            throw new ErrorException(OrderErrorCode.ORDER_NOT_FOUND);
        }
        if (!order.getId().equals(request.orderId())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "orderId 값이 주문 정보와 일치하지 않습니다.");
        }
        if (!OrderStatusPolicy.PAYMENT_CONFIRMABLE_STATUSES.contains(order.getStatus())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "결제 확정 요청 가능한 주문 상태가 아닙니다.");
        }
    }

    private void validateStablePaymentConfirmRequest(OrderPaymentConfirmRequest request) {
        if (isBlank(request.paymentKey())) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_STATE, "STABLE 결제는 paymentKey 값이 필요합니다.");
        }
    }

    private void pause(long intervalMs) {
        try {
            Thread.sleep(Math.max(10, intervalMs));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ErrorException(OrderErrorCode.INVENTORY_PREVIEW_UNAVAILABLE, "결제 확인 대기 중 인터럽트가 발생했습니다.");
        }
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

        return OrderEntity.create(
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

    private List<Map<String, Object>> appendItemsAndBuildReservePayload(
            OrderEntity order,
            List<OrderCreateItemRequest> requestItems
    ) {
        List<Map<String, Object>> payloadItems = new ArrayList<>(requestItems.size());
        for (OrderCreateItemRequest requestItem : requestItems) {
            BigDecimal lineAmount = requestItem.unitPrice().multiply(BigDecimal.valueOf(requestItem.quantity()));
            order.addItem(
                    requestItem.productId(),
                    requestItem.optionId(),
                    requestItem.sellerId(),
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

    private Map<String, Object> toReservePayloadItem(OrderCreateItemRequest requestItem) {
        Map<String, Object> payloadItem = new LinkedHashMap<>();
        payloadItem.put("productId", requestItem.productId());
        payloadItem.put("optionId", requestItem.optionId());
        payloadItem.put("sellerId", requestItem.sellerId());
        payloadItem.put("quantity", requestItem.quantity());
        return payloadItem;
    }

    private void saveCreatedOrder(OrderEntity order, String eventId) {
        orderRepository.save(order);
        saveStatusHistory(order, null, OrderStatus.PENDING_RESERVE, eventId, REASON_ORDER_CREATED);
    }

    private void startSaga(OrderEntity order, String eventId) {
        String sagaId = OrderIdGenerator.newSagaId();
        OrderSagaEntity saga = OrderSagaEntity.create(
                order,
                sagaId,
                OrderSagaState.WAITING_INVENTORY,
                eventId
        );
        orderSagaRepository.save(saga);
    }

    private void publishInventoryReserveRequested(
            OrderEntity order,
            OrderCreateRequest request,
            OrderCreateContext context,
            List<Map<String, Object>> payloadItems
    ) {
        Map<String, Object> payload = buildInventoryReservePayload(order, request, context, payloadItems);
        publishDomainEvent(
                context.eventId(),
                OrderEventType.INVENTORY_RESERVE_REQUESTED,
                order.getId(),
                kafkaTopicsProperties.getInventoryReserveTopic(),
                payload
        );
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
        Map<String, Object> releasePayload = new LinkedHashMap<>();
        releasePayload.put("orderId", order.getId());
        releasePayload.put("orderNo", order.getOrderNo());
        releasePayload.put("reasonCode", cancelReasonCode);

        publishDomainEvent(
                releaseEventId,
                OrderEventType.INVENTORY_RELEASE_REQUESTED,
                order.getId(),
                kafkaTopicsProperties.getInventoryCommandTopic(),
                releasePayload
        );
    }

    private Map<String, Object> buildInventoryReservePayload(
            OrderEntity order,
            OrderCreateRequest request,
            OrderCreateContext context,
            List<Map<String, Object>> payloadItems
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.getId());
        payload.put("orderNo", order.getOrderNo());
        payload.put("buyerId", request.buyerId());
        payload.put("payAmount", context.amountSummary().payAmount());
        payload.put("paymentMethod", request.paymentMethod() == null ? null : request.paymentMethod().name());
        payload.put("recipientName", request.recipientName());
        payload.put("recipientPhone", request.recipientPhone());
        payload.put("zipCode", request.zipCode());
        payload.put("address1", request.address1());
        payload.put("address2", request.address2());
        payload.put("orderedAt", context.orderedAt());
        payload.put("items", payloadItems);
        return payload;
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

    private void publishDomainEvent(
            String eventId,
            String eventType,
            Long orderId,
            String topic,
            Map<String, Object> payload
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
        orderStatusHistoryRepository.save(OrderStatusHistoryEntity.create(
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
                    || item.sellerId() == null
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

    private PaymentMethod resolvePaymentMethod(OrderEntity order) {
        if (order.getOrderInfo() == null || order.getOrderInfo().getPaymentMethod() == null) {
            return PaymentMethod.CARD;
        }
        return order.getOrderInfo().getPaymentMethod();
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

        private static final Set<OrderStatus> PAYMENT_CONFIRMABLE_STATUSES = Set.of(
                OrderStatus.PAYMENT_PENDING,
                OrderStatus.PAYMENT_FAILED,
                OrderStatus.CONFIRMED
        );
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
