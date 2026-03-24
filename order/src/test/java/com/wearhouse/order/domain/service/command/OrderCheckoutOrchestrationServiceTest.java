package com.wearhouse.order.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.domain.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.PaymentConfirmInternalResponse;
import com.wearhouse.order.infra.payment.dto.PaymentPrepareInternalResponse;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import com.wearhouse.order.support.config.OrderProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderCommandServiceOrchestrationTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderPaymentClient orderPaymentClient;
    @Mock
    private OrderSagaRepository orderSagaRepository;
    @Mock
    private OrderStatusHistoryRepository orderStatusHistoryRepository;
    @Mock
    private OrderDomainEventPublisher orderDomainEventPublisher;

    private OrderCommandService orderCommandService;

    @BeforeEach
    void setUp() {
        OrderKafkaTopicsProperties topicsProperties = new OrderKafkaTopicsProperties();
        topicsProperties.setInventoryReserveTopic("inventory.reserve.topic");
        topicsProperties.setInventoryCommandTopic("inventory.command.topic");

        OrderProperties orderProperties = new OrderProperties();
        orderProperties.getInternal().setSharedSecret("internal-secret");
        orderProperties.setPaymentConfirmWaitTimeoutMs(1000L);
        orderProperties.setPaymentConfirmWaitIntervalMs(10L);

        orderCommandService = new OrderCommandService(
                orderRepository,
                orderSagaRepository,
                orderStatusHistoryRepository,
                orderDomainEventPublisher,
                orderPaymentClient,
                topicsProperties,
                orderProperties
        );
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    void 주문생성은_즉시_응답한다() {
        OrderCreateRequest request = sampleRequest(PaymentMethod.STABLE);
        OrderCreateResponse result = orderCommandService.createOrder(request);

        assertThat(result.orderNo()).isNotBlank();
        assertThat(result.customerId()).isNotBlank();
        assertThat(result.customerName()).isEqualTo("tester");
        assertThat(result.payAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        verify(orderDomainEventPublisher).publish(any());
    }

    @Test
    void 결제확정_요청후_confirmed로_전이되면_반환한다() {
        OrderEntity paymentPending = mock(OrderEntity.class);
        given(paymentPending.getStatus()).willReturn(OrderStatus.PAYMENT_PENDING);
        given(paymentPending.getId()).willReturn(1L);
        given(paymentPending.getBuyerId()).willReturn(1L);
        OrderInfo stableInfo = mock(OrderInfo.class);
        given(stableInfo.getPaymentMethod()).willReturn(PaymentMethod.STABLE);
        given(paymentPending.getOrderInfo()).willReturn(stableInfo);

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

        OrderPaymentConfirmResponse response = orderCommandService.confirmPayment(
                1L,
                "O202603190001",
                new OrderPaymentConfirmRequest(1L, "pay_key_1", new BigDecimal("10000"))
        );

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED.name());
        verify(orderPaymentClient).confirmStablepayPayment(eq("internal-secret"), any());
    }

    @Test
    void 카드결제_확정요청은_payment_확인_API를_호출하지_않는다() {
        OrderEntity paymentPending = mock(OrderEntity.class);
        given(paymentPending.getStatus()).willReturn(OrderStatus.PAYMENT_PENDING);
        given(paymentPending.getId()).willReturn(2L);
        given(paymentPending.getBuyerId()).willReturn(1L);
        OrderInfo cardInfo = mock(OrderInfo.class);
        given(cardInfo.getPaymentMethod()).willReturn(PaymentMethod.CARD);
        given(paymentPending.getOrderInfo()).willReturn(cardInfo);

        OrderEntity confirmed = mock(OrderEntity.class);
        given(confirmed.getStatus()).willReturn(OrderStatus.CONFIRMED);
        given(confirmed.getFailReasonCode()).willReturn(null);

        given(orderRepository.findDetailByOrderNo("O202603190002")).willReturn(Optional.of(paymentPending));
        given(orderRepository.findDetailById(2L)).willReturn(Optional.of(confirmed));

        OrderPaymentConfirmResponse response = orderCommandService.confirmPayment(
                1L,
                "O202603190002",
                new OrderPaymentConfirmRequest(2L, null, new BigDecimal("10000"))
        );

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED.name());
        verify(orderPaymentClient, never()).confirmStablepayPayment(any(), any());
    }

    @Test
    void stable_prepare는_payment_prepare_internal_api를_호출하고_checkout_url을_반환한다() {
        OrderEntity paymentPending = mock(OrderEntity.class);
        given(paymentPending.getStatus()).willReturn(OrderStatus.PAYMENT_PENDING);
        given(paymentPending.getId()).willReturn(3L);
        given(paymentPending.getOrderNo()).willReturn("O202603190003");
        given(paymentPending.getBuyerId()).willReturn(1L);
        given(paymentPending.getTotalAmount()).willReturn(new BigDecimal("10000"));
        OrderInfo stableInfo = mock(OrderInfo.class);
        given(stableInfo.getPaymentMethod()).willReturn(PaymentMethod.STABLE);
        given(stableInfo.getRecipientName()).willReturn("tester");
        given(paymentPending.getOrderInfo()).willReturn(stableInfo);

        given(orderRepository.findDetailByOrderNo("O202603190003")).willReturn(Optional.of(paymentPending));
        given(orderPaymentClient.prepareStablepayPayment(eq("internal-secret"), any()))
                .willReturn(ApiResponse.success(new PaymentPrepareInternalResponse(
                        "cs_3",
                        "https://wallet.example/checkout/cs_3",
                        "wallet://checkout/cs_3",
                        null
                )));

        OrderPaymentPrepareResponse response = orderCommandService.preparePayment(1L, "O202603190003", "idem-3");

        assertThat(response.checkoutSessionId()).isEqualTo("cs_3");
        assertThat(response.checkoutUrl()).isEqualTo("https://wallet.example/checkout/cs_3");
        verify(orderPaymentClient).prepareStablepayPayment(eq("internal-secret"), any());
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

    private OrderEntity statusOrder(OrderStatus status) {
        OrderEntity order = mock(OrderEntity.class);
        given(order.getStatus()).willReturn(status);
        return order;
    }
}
