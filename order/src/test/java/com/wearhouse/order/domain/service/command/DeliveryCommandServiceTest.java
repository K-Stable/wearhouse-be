package com.wearhouse.order.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.order.domain.dto.request.DeliveryDeliveredRequest;
import com.wearhouse.order.domain.dto.request.DeliveryRegisterRequest;
import com.wearhouse.order.domain.dto.response.DeliveryBatchUpdateResponse;
import com.wearhouse.order.domain.entity.DeliveryEntity;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.DeliveryStatus;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.infra.jpa.repository.DeliveryRepository;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.support.config.OrderProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class DeliveryCommandServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private DeliveryRepository deliveryRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;

    private DeliveryCommandService deliveryCommandService;

    @BeforeEach
    void setUp() {
        OrderProperties orderProperties = new OrderProperties();
        orderProperties.setDeliveryPurchaseConfirmDelayDays(7L);
        deliveryCommandService = new DeliveryCommandService(
                orderRepository,
                deliveryRepository,
                orderStatusHistoryRepository,
                orderProperties
        );
    }

    @Test
    void 배송등록시_판매자_주문이면_배송정보를_저장한다() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        OrderEntity order = mockOrder(101L, OrderStatus.CONFIRMED);
        DeliveryRegisterRequest request = new DeliveryRegisterRequest(List.of(
                new DeliveryRegisterRequest.DeliveryRegisterItemRequest(101L, "CJ", "INV-101")
        ));

        given(orderRepository.findDetailsByIdIn(any())).willReturn(List.of(order));
        given(deliveryRepository.findByOrder_IdIn(any())).willReturn(List.of());
        given(deliveryRepository.save(any(DeliveryEntity.class))).willAnswer(invocation -> invocation.getArgument(0));

        DeliveryBatchUpdateResponse response = deliveryCommandService.registerDeliveries(seller, request);

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(response.orderIds()).containsExactly(101L);
        verify(deliveryRepository).save(any(DeliveryEntity.class));
    }

    @Test
    void 배송완료처리시_배송상태와_주문상태를_갱신한다() {
        LoginUser seller = new LoginUser(21L, "SELLER", List.of("ROLE_SELLER"), 1L);
        OrderEntity order = mockOrder(201L, OrderStatus.CONFIRMED);
        DeliveryEntity delivery = DeliveryEntity.create(order, "CJ", "INV-201", DeliveryStatus.IN_DELIVERY);
        DeliveryDeliveredRequest request = new DeliveryDeliveredRequest(List.of(201L));

        given(orderRepository.findDetailsByIdIn(any())).willReturn(List.of(order));
        given(deliveryRepository.findByOrder_IdIn(any())).willReturn(List.of(delivery));

        DeliveryBatchUpdateResponse response = deliveryCommandService.markDelivered(seller, request);

        assertThat(response.processedCount()).isEqualTo(1);
        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.DELIVERED);
        verify(order).updateStatus(eq(OrderStatus.DELIVERED), eq(null), eq(null), eq(null));
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    void 배송완료_D플러스7_경과시_자동구매확정으로_전이한다() {
        OrderEntity deliveredOrder = mock(OrderEntity.class);
        given(deliveredOrder.getStatus()).willReturn(OrderStatus.DELIVERED);
        DeliveryEntity delivered = DeliveryEntity.create(
                deliveredOrder,
                "CJ",
                "INV-301",
                DeliveryStatus.DELIVERED
        );
        given(deliveryRepository.findAutoConfirmTargets(
                eq(DeliveryStatus.DELIVERED),
                any(),
                eq(OrderStatus.DELIVERED),
                eq(PageRequest.of(0, 100))
        )).willReturn(List.of(delivered));

        int processed = deliveryCommandService.autoConfirmDeliveredOrders();

        assertThat(processed).isEqualTo(1);
        verify(deliveredOrder).updateStatus(eq(OrderStatus.PURCHASE_CONFIRMED), eq(null), eq(null), eq(null));
        verify(orderStatusHistoryRepository).save(any());
    }

    @Test
    void 배송시작된_주문은_취소차단여부가_true다() {
        given(deliveryRepository.existsByOrder_IdAndStatusIn(eq(401L), any()))
                .willReturn(true);

        boolean blocked = deliveryCommandService.isCancelBlockedByDelivery(401L);

        assertThat(blocked).isTrue();
    }

    @Test
    void 판매자_아니어도_배송등록할_수_있다() {
        LoginUser buyer = new LoginUser(1L, "BUYER", List.of("ROLE_BUYER"), 1L);
        OrderEntity order = mockOrder(1L, OrderStatus.CONFIRMED);
        DeliveryRegisterRequest request = new DeliveryRegisterRequest(List.of(
                new DeliveryRegisterRequest.DeliveryRegisterItemRequest(1L, "CJ", "INV-1")
        ));

        given(orderRepository.findDetailsByIdIn(any())).willReturn(List.of(order));
        given(deliveryRepository.findByOrder_IdIn(any())).willReturn(List.of());
        given(deliveryRepository.save(any(DeliveryEntity.class))).willAnswer(invocation -> invocation.getArgument(0));

        DeliveryBatchUpdateResponse response = deliveryCommandService.registerDeliveries(buyer, request);

        assertThat(response.processedCount()).isEqualTo(1);
    }

    private OrderEntity mockOrder(Long orderId, OrderStatus status) {
        OrderEntity order = mock(OrderEntity.class);
        given(order.getId()).willReturn(orderId);
        given(order.getStatus()).willReturn(status);
        return order;
    }
}
