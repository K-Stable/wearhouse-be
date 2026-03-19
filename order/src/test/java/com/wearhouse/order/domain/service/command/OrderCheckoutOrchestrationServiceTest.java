package com.wearhouse.order.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.StablepaySessionPrepareResponse;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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
    private OrderPaymentClient orderPaymentClient;

    private OrderCheckoutOrchestrationService orchestrationService;

    @BeforeEach
    void setUp() {
        orchestrationService = new OrderCheckoutOrchestrationService(orderCommandService, orderPaymentClient);
        ReflectionTestUtils.setField(orchestrationService, "orderInternalSharedSecret", "internal-secret");
        ReflectionTestUtils.setField(orchestrationService, "defaultTokenAddress", "0xtoken");
        ReflectionTestUtils.setField(orchestrationService, "defaultChainId", "8453");
    }

    @Test
    void stablepay_주문은_세션정보를_응답에_포함한다() {
        OrderCreateRequest request = sampleRequest(PaymentMethod.STABLEPAY);
        OrderCreateResponse created = createdOrderResponse();
        given(orderCommandService.createOrder(any(OrderCreateRequest.class))).willReturn(created);
        given(orderPaymentClient.prepareStablepaySession(eq("internal-secret"), any()))
                .willReturn(ApiResponse.success(new StablepaySessionPrepareResponse(
                        "pay_1",
                        "PAY123",
                        "ps_1",
                        "merchant_1",
                        "nonce_1",
                        "2026-03-20T00:00:00Z",
                        "hash_1"
                )));

        OrderCreateResponse result = orchestrationService.createOrder(request);

        assertThat(result.paymentSessionId()).isEqualTo("ps_1");
        assertThat(result.paymentKey()).isEqualTo("pay_1");
        verify(orderPaymentClient).prepareStablepaySession(eq("internal-secret"), any());
    }

    @Test
    void card_주문은_세션준비를_호출하지_않는다() {
        OrderCreateRequest request = sampleRequest(PaymentMethod.CARD);
        OrderCreateResponse created = createdOrderResponse();
        given(orderCommandService.createOrder(any(OrderCreateRequest.class))).willReturn(created);

        OrderCreateResponse result = orchestrationService.createOrder(request);

        assertThat(result.orderNo()).isEqualTo(created.orderNo());
        assertThat(result.paymentSessionId()).isNull();
        verifyNoInteractions(orderPaymentClient);
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
                .payerAddress("0xpayer")
                .tokenAddress("0xtoken")
                .chainId("8453")
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
}
