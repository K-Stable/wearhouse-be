package com.wearhouse.order.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.inventory.InventoryStockFeignClient;
import com.wearhouse.common.infra.feign.inventory.dto.InventorySellerResolveResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventorySellerResolveResponse.InventorySkuSellerLine;
import com.wearhouse.order.buyer.dto.request.OrderCreateRequest;
import com.wearhouse.order.buyer.dto.request.OrderPaymentConfirmRequest;
import com.wearhouse.order.buyer.dto.response.OrderCreateResponse;
import com.wearhouse.order.buyer.dto.response.OrderPaymentConfirmResponse;
import com.wearhouse.order.buyer.dto.response.OrderPaymentPrepareResponse;
import com.wearhouse.order.buyer.service.OrderCreateOrchestrationService;
import com.wearhouse.order.domain.entity.OrderEntity;
import com.wearhouse.order.domain.entity.OrderInfo;
import com.wearhouse.order.domain.event.OrderDomainEventPublisher;
import com.wearhouse.order.domain.model.OrderStatus;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.jpa.repository.OrderRepository;
import com.wearhouse.order.infra.jpa.repository.OrderSagaRepository;
import com.wearhouse.order.infra.jpa.repository.OrderStatusHistoryRepository;
import com.wearhouse.order.payment.client.OrderPaymentClient;
import com.wearhouse.order.payment.service.OrderPaymentOrchestrationService;
import com.wearhouse.order.payment.dto.response.PaymentConfirmInternalResponse;
import com.wearhouse.order.payment.dto.response.PaymentPrepareInternalResponse;
import com.wearhouse.order.support.config.OrderKafkaTopicsProperties;
import com.wearhouse.order.support.config.OrderProperties;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.util.ReflectionTestUtils;

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
    @Mock
    private InventoryStockFeignClient inventoryStockFeignClient;
    @Mock
    private TransactionTemplate transactionTemplate;
    @Mock
    private EntityManager entityManager;

    private OrderPaymentOrchestrationService orderPaymentOrchestrationService;
    private OrderCreateOrchestrationService orderCreateOrchestrationService;

    @BeforeEach
    void setUp() {
        OrderKafkaTopicsProperties topicsProperties = new OrderKafkaTopicsProperties();
        topicsProperties.setInventoryReserveTopic("inventory.reserve.topic");
        topicsProperties.setInventoryCommandTopic("inventory.command.topic");

        OrderProperties orderProperties = new OrderProperties();
        orderProperties.getInternal().setSharedSecret("internal-secret");
        orderProperties.setPaymentConfirmWaitTimeoutMs(1000L);
        orderProperties.setPaymentConfirmWaitIntervalMs(10L);
        orderProperties.setPaymentPrepareWaitTimeoutMs(1000L);
        orderProperties.setPaymentPrepareWaitIntervalMs(10L);

        lenient().when(transactionTemplate.execute(any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<Object> callback = invocation.getArgument(0);
                    return callback.doInTransaction(mock(TransactionStatus.class));
                });

        orderPaymentOrchestrationService = new OrderPaymentOrchestrationService(
                orderRepository,
                orderStatusHistoryRepository,
                orderSagaRepository,
                orderPaymentClient,
                orderProperties,
                entityManager
        );
        orderCreateOrchestrationService = new OrderCreateOrchestrationService(
                orderRepository,
                orderSagaRepository,
                orderStatusHistoryRepository,
                orderDomainEventPublisher,
                inventoryStockFeignClient,
                topicsProperties,
                transactionTemplate,
                orderPaymentOrchestrationService
        );
        ReflectionTestUtils.setField(orderCreateOrchestrationService, "inventoryInternalSharedSecret", "inventory-secret");
        lenient().when(inventoryStockFeignClient.resolveSellers(any(), any()))
                .thenReturn(ApiResponse.success(new InventorySellerResolveResponse(
                        List.of(new InventorySkuSellerLine(2L, 10L))
                )));
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
    }

    @Test
    void 주문생성은_즉시_응답한다() {
        OrderCreateRequest request = sampleRequest(PaymentMethod.CARD);
        OrderCreateResponse result = orderCreateOrchestrationService.createOrder(request);

        assertThat(result.orderNo()).isNotBlank();
        assertThat(result.customerId()).isNotBlank();
        assertThat(result.customerName()).isEqualTo("tester");
        assertThat(result.payAmount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.checkoutUrl()).isNull();
        verify(orderDomainEventPublisher).publish(any());
    }

    @Test
    void stable_주문생성은_재고예약후_prepare를_거쳐_checkout_url을_반환한다() {
        OrderCreateRequest request = sampleRequest(PaymentMethod.STABLE);

        OrderEntity paymentPending = mock(OrderEntity.class);
        given(paymentPending.getStatus()).willReturn(OrderStatus.PAYMENT_PENDING);
        given(paymentPending.getId()).willReturn(10L);
        given(paymentPending.getOrderNo()).willReturn("O-STABLE-1");
        given(paymentPending.getBuyerId()).willReturn(1L);
        given(paymentPending.getTotalAmount()).willReturn(new BigDecimal("1000"));
        OrderInfo stableInfo = mock(OrderInfo.class);
        given(stableInfo.getPaymentMethod()).willReturn(PaymentMethod.STABLE);
        given(stableInfo.getRecipientName()).willReturn("tester");
        given(paymentPending.getOrderInfo()).willReturn(stableInfo);

        given(orderRepository.findDetailByOrderNo(any())).willReturn(Optional.of(paymentPending));
        given(orderPaymentClient.prepareStablepayPayment(eq("internal-secret"), any()))
                .willReturn(ApiResponse.success(new PaymentPrepareInternalResponse(
                        "cs-created",
                        "https://wallet.example/checkout/cs-created",
                        "wallet://checkout/cs-created",
                        "2026-03-26T00:00:00Z"
                )));

        OrderCreateResponse result = orderCreateOrchestrationService.createOrder(request);

        assertThat(result.orderNo()).isNotBlank();
        assertThat(result.checkoutSessionId()).isEqualTo("cs-created");
        assertThat(result.checkoutUrl()).isEqualTo("https://wallet.example/checkout/cs-created");
        verify(orderPaymentClient).prepareStablepayPayment(eq("internal-secret"), any());
    }

    @Test
    void 결제확정_요청후_confirmed로_전이되면_반환한다() {
        OrderEntity paymentPending = mock(OrderEntity.class);
        given(paymentPending.getStatus()).willReturn(OrderStatus.PAYMENT_PENDING);
        given(paymentPending.getId()).willReturn(1L);
        given(paymentPending.getBuyerId()).willReturn(1L);
        given(paymentPending.getOrderNo()).willReturn("O202603190001");
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

        OrderPaymentConfirmResponse response = orderPaymentOrchestrationService.confirmPayment(
                1L,
                "O202603190001",
                new OrderPaymentConfirmRequest("O202603190001", "pay_key_1", new BigDecimal("10000"))
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
        given(paymentPending.getOrderNo()).willReturn("O202603190002");
        OrderInfo cardInfo = mock(OrderInfo.class);
        given(cardInfo.getPaymentMethod()).willReturn(PaymentMethod.CARD);
        given(paymentPending.getOrderInfo()).willReturn(cardInfo);

        OrderEntity confirmed = mock(OrderEntity.class);
        given(confirmed.getStatus()).willReturn(OrderStatus.CONFIRMED);
        given(confirmed.getFailReasonCode()).willReturn(null);

        given(orderRepository.findDetailByOrderNo("O202603190002")).willReturn(Optional.of(paymentPending));
        given(orderRepository.findDetailById(2L)).willReturn(Optional.of(confirmed));

        OrderPaymentConfirmResponse response = orderPaymentOrchestrationService.confirmPayment(
                1L,
                "O202603190002",
                new OrderPaymentConfirmRequest("O202603190002", "card_ignore", new BigDecimal("10000"))
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

        OrderPaymentPrepareResponse response = orderPaymentOrchestrationService.preparePayment(1L, "O202603190003", "idem-3");

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
                        .productName("상품")
                        .optionName("옵션")
                        .unitPrice(new BigDecimal("1000"))
                        .quantity(1)
                        .build()))
                .build();
    }

}
