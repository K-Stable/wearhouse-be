package com.wearhouse.order.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.entity.OrderSagaEntity;
import com.wearhouse.order.domain.event.OrderDomainEvent;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.model.OrderItemStatus;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.OrderInboxRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderSagaServiceFlowTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderSagaRepository orderSagaRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private OrderInboxRepository orderInboxRepository;
    @Mock
    private OrderDomainEventPublisher orderDomainEventPublisher;

    private OrderSagaService orderSagaService;

    @BeforeEach
    void setUp() {
        orderSagaService = new OrderSagaService(
                orderRepository,
                orderSagaRepository,
                orderStatusHistoryRepository,
                orderInboxRepository,
                orderDomainEventPublisher,
                "wearhouse.payment.command.v1",
                "wearhouse.inventory.command.v1",
                "wearhouse.order.event.v1"
        );
    }

    @Test
    void 결제성공_플로우_재고예약후_주문확정으로_수렴한다() {
        OrderEntity order = testOrder(1L, OrderStatus.PENDING_RESERVE);
        OrderSagaEntity saga = mock(OrderSagaEntity.class);
        stubCommon(order, saga);

        Map<String, Object> payload = payload(order.getId(), order.getOrderNo());
        orderSagaService.onInventoryEvent("inv-evt-1", "StockReserved", "inventory-event", "1", "{}", payload);
        orderSagaService.onPaymentEvent("pay-evt-1", "PaymentAuthorized", "payment-event", "1", "{}", payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getConfirmedAt()).isNotNull();
        assertThat(order.getItems()).extracting("status").containsOnly(OrderItemStatus.CONFIRMED);

        ArgumentCaptor<OrderDomainEvent> eventCaptor = ArgumentCaptor.forClass(OrderDomainEvent.class);
        verify(orderDomainEventPublisher, times(2)).publish(eventCaptor.capture());
        List<String> eventTypes = eventCaptor.getAllValues().stream()
                .map(OrderDomainEvent::getEventType)
                .toList();
        assertThat(eventTypes).containsExactly("PaymentPrepareRequested", "OrderConfirmed");

        verify(orderStatusHistoryRepository, atLeast(2)).save(any());
        verify(orderInboxRepository).markProcessed("inv-evt-1", "order-inventory-consumer");
        verify(orderInboxRepository).markProcessed("pay-evt-1", "order-payment-consumer");
    }

    @Test
    void 결제실패_플로우_보상후_주문서_복귀가능상태를_유지한다() {
        OrderEntity order = testOrder(2L, OrderStatus.PENDING_RESERVE);
        OrderSagaEntity saga = mock(OrderSagaEntity.class);
        stubCommon(order, saga);

        Map<String, Object> payload = payload(order.getId(), order.getOrderNo());
        orderSagaService.onInventoryEvent("inv-evt-2", "StockReserved", "inventory-event", "2", "{}", payload);

        Map<String, Object> paymentFailedPayload = payload(order.getId(), order.getOrderNo());
        paymentFailedPayload.put("reasonCode", "PAYMENT_FAILED");
        orderSagaService.onPaymentEvent("pay-evt-2", "PaymentFailed", "payment-event", "2", "{}", paymentFailedPayload);

        orderSagaService.onInventoryEvent("inv-evt-3", "InventoryReleased", "inventory-event", "2", "{}", payload);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
        assertThat(order.getCancelledAt()).isNull();
        assertThat(order.getItems()).extracting("status").containsOnly(OrderItemStatus.PENDING_RESERVE);

        ArgumentCaptor<OrderDomainEvent> eventCaptor = ArgumentCaptor.forClass(OrderDomainEvent.class);
        verify(orderDomainEventPublisher, times(2)).publish(eventCaptor.capture());
        List<String> eventTypes = eventCaptor.getAllValues().stream()
                .map(OrderDomainEvent::getEventType)
                .toList();
        assertThat(eventTypes).containsExactly(
                "PaymentPrepareRequested",
                "InventoryReleaseRequested"
        );

        verify(orderStatusHistoryRepository, atLeast(2)).save(any());
        verify(orderInboxRepository).markProcessed("inv-evt-2", "order-inventory-consumer");
        verify(orderInboxRepository).markProcessed("pay-evt-2", "order-payment-consumer");
        verify(orderInboxRepository).markProcessed("inv-evt-3", "order-inventory-consumer");
    }

    private void stubCommon(OrderEntity order, OrderSagaEntity saga) {
        when(orderInboxRepository.tryReceive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(orderRepository.findDetailById(eq(order.getId()))).thenReturn(Optional.of(order));
        when(orderSagaRepository.findByOrder_Id(eq(order.getId()))).thenReturn(Optional.of(saga));
        when(orderStatusHistoryRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private OrderEntity testOrder(Long orderId, OrderStatus status) {
        OrderEntity order = OrderEntity.create(
                "ORDER-" + orderId,
                1000L + orderId,
                status,
                new BigDecimal("10000"),
                "KRW",
                new BigDecimal("10000"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                OrderInfo.of("CARD", "tester", "01012345678", "12345", "Seoul", "Gangnam", null),
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(order, "id", orderId);
        order.addItem(
                3000L + orderId,
                4000L + orderId,
                5000L + orderId,
                "item-" + orderId,
                "opt-" + orderId,
                new BigDecimal("10000"),
                1,
                new BigDecimal("10000")
        );
        return order;
    }

    private Map<String, Object> payload(Long orderId, String orderNo) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("items", new ArrayList<>());
        return payload;
    }
}
