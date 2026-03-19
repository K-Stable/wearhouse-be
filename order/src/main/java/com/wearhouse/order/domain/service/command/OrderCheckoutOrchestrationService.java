package com.wearhouse.order.domain.service.command;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.order.domain.dto.request.OrderCreateRequest;
import com.wearhouse.order.domain.dto.response.OrderCreateResponse;
import com.wearhouse.order.domain.exception.OrderErrorCode;
import com.wearhouse.order.domain.model.PaymentMethod;
import com.wearhouse.order.infra.payment.OrderPaymentClient;
import com.wearhouse.order.infra.payment.dto.StablepaySessionPrepareRequest;
import com.wearhouse.order.infra.payment.dto.StablepaySessionPrepareResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderCheckoutOrchestrationService {

    private final OrderCommandService orderCommandService;
    private final OrderPaymentClient orderPaymentClient;

    @Value("${wearhouse.order.internal.shared-secret:wearhouse-order-internal-secret}")
    private String orderInternalSharedSecret;

    @Value("${wearhouse.order.stablepay.default-token-address:0x0000000000000000000000000000000000000000}")
    private String defaultTokenAddress;

    @Value("${wearhouse.order.stablepay.default-chain-id:8453}")
    private String defaultChainId;

    public OrderCreateResponse createOrder(OrderCreateRequest request) {
        OrderCreateResponse created = orderCommandService.createOrder(request);
        if (request.paymentMethod() != PaymentMethod.STABLEPAY) {
            return created;
        }

        String payerAddress = requireNonBlank(request.payerAddress());
        String tokenAddress = request.tokenAddress() == null || request.tokenAddress().isBlank()
                ? defaultTokenAddress
                : request.tokenAddress();
        String chainId = request.chainId() == null || request.chainId().isBlank()
                ? defaultChainId
                : request.chainId();

        StablepaySessionPrepareRequest prepareRequest = new StablepaySessionPrepareRequest(
                created.orderId(),
                created.orderNo(),
                created.payAmount(),
                payerAddress,
                tokenAddress,
                chainId
        );
        ApiResponse<StablepaySessionPrepareResponse> response = orderPaymentClient.prepareStablepaySession(
                orderInternalSharedSecret,
                prepareRequest
        );

        if (response == null || !response.success() || response.data() == null) {
            throw new ErrorException(OrderErrorCode.OUTBOX_PUBLISH_FAILED, "StablePay 세션 준비에 실패했습니다.");
        }

        StablepaySessionPrepareResponse session = response.data();
        return created.withStablepaySession(
                session.paymentKey(),
                session.paymentId(),
                session.paymentSessionId(),
                session.merchantKey(),
                session.nonce(),
                session.deadline(),
                session.payloadHash()
        );
    }

    private String requireNonBlank(String value) {
        if (value == null || value.isBlank()) {
            throw new ErrorException(OrderErrorCode.INVALID_ORDER_AMOUNT, "StablePay 결제를 위해 payerAddress가 필요합니다.");
        }
        return value;
    }
}
