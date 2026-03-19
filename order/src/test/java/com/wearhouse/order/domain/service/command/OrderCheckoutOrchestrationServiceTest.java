package com.wearhouse.order.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class OrderCheckoutOrchestrationServiceTest {

    @Mock
    private OrderCommandService orderCommandService;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderPaymentClient orderPaymentClient;

    private OrderCheckoutOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new OrderCheckoutOrchestrationService(orderCommandService, orderRepository, orderPaymentClient);
        ReflectionTestUtils.setField(orchestrationService, "orderInternalSharedSecret", "internal-secret");
        ReflectionTestUtils.setField(orchestrationService, "reserveWaitTimeoutMs", 1000L);
        ReflectionTestUtils.setField(orchestrationService, "reserveWaitIntervalMs", 10L);
        ReflectionTestUtils.setField(orchestrationService, "paymentConfirmWaitTimeoutMs", 1000L);
        ReflectionTestUtils.setField(orchestrationService, "paymentConfirmWaitIntervalMs", 10L);
    }

    @Test
    void stablepay_주문은_payment_pending까지_대기한다() {
        OrderCreateRequest request = sampleRequest(PaymentMethod.STABLEPAY);
        OrderCreateResponse created = createdOrderResponse();
        OrderEntity pendingReserve = statusOrder(OrderStatus.PENDING_RESERVE);
        OrderEntity paymentPending = statusOrder(OrderStatus.PAYMENT_PENDING);
        given(orderCommandService.createOrder(any(OrderCreateRequest.class))).willReturn(created);
        given(orderRepository.findDetailById(1L)).willReturn(Optional.of(pendingReserve), Optional.of(paymentPending));

        OrderCreateResponse result = orchestrationService.createOrder(request);

        assertThat(result.status()).isEqualTo(OrderStatus.PAYMENT_PENDING.name());
    }

    @Test
    void 결제확정_요청후_confirmed로_전이되면_반환한다() {
        OrderEntity paymentPending = mock(OrderEntity.class);
        given(paymentPending.getStatus()).willReturn(OrderStatus.PAYMENT_PENDING);
        given(paymentPending.getId()).willReturn(1L);
        given(paymentPending.getBuyerId()).willReturn(1L);

        OrderEntity confirmed = mock(OrderEntity.class);
        given(confirmed.getStatus()).willReturn(OrderStatus.CONFIRMED);
        given(confirmed.getFailReasonCode()).willReturn(null);

        given(orderRepository.findDetailByOrderNo("O202603190001")).willReturn(Optional.of(paymentPending));
        given(orderPaymentClient.confirmStablepayPayment(eq("internal-secret"), any()))
                .willReturn(ApiResponse.success(new PaymentConfirmInternalResponse(
                        1L,
                        "O202603190001",
                        "pid_1",
                        "AUTHORIZED",
                        "authorized_confirmed",
                        null
                )));
        given(orderRepository.findDetailById(1L)).willReturn(Optional.of(confirmed));

        OrderPaymentConfirmResponse response = orchestrationService.confirmStablepayPayment(
                1L,
                "O202603190001",
                new OrderPaymentConfirmRequest(1L, "pay_key_1", new BigDecimal("10000"))
        );

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED.name());
        verify(orderPaymentClient).confirmStablepayPayment(eq("internal-secret"), any());
    }

    private OrderCreateRequest sampleRequest(PaymentMethod paymentMethod) {
        return OrderCreateRequest.builder()
                .buyerId(1L)
                .paymentMethod(paymentMethod)
                .recipientName("tester")
                .recipientPhone("01012345678")
                .zipCode("12345")
                .address1("Seoul")
                .address2("Gangnam")
                .deliveryRequest("문 앞")
                .shippingFee(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .pointUsedAmount(BigDecimal.ZERO)
                .items(List.of(OrderCreateRequest.OrderCreateItemRequest.builder()
                        .productId(1L)
                        .optionId(2L)
                        .sellerId(3L)
                        .productName("상품")
                        .optionName("옵션")
                        .unitPrice(new BigDecimal("1000"))
                        .quantity(1)
                        .build()))
                .build();
    }

    private OrderCreateResponse createdOrderResponse() {
        return OrderCreateResponse.builder()
                .orderId(1L)
                .orderNo("O202603190001")
                .status("PENDING_RESERVE")
                .payAmount(new BigDecimal("1000"))
                .sagaId("SAGA01")
                .outboxEventId("OUTB01")
                .orderedAt(LocalDateTime.now())
                .build();
    }

    private OrderEntity statusOrder(OrderStatus status) {
        OrderEntity order = mock(OrderEntity.class);
        given(order.getStatus()).willReturn(status);
        return order;
    }
}
