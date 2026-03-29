package com.wearhouse.payment.internal.service;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentMethod;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.internal.dto.request.WalletPrepareRequest;
import com.wearhouse.payment.internal.dto.response.WalletPrepareResponse;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.stablepay.client.WalletServerGateway;
import com.wearhouse.payment.support.config.PaymentMockProperties;
import com.wearhouse.payment.support.config.PaymentOrderInternalProperties;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import com.wearhouse.payment.transaction.service.PaymentTransactionUpdateService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalPrepareService {

    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";

    private final PaymentTransactionCreateService paymentTransactionCreateService;
    private final PaymentTransactionUpdateService paymentTransactionUpdateService;
    private final WalletServerGateway walletServerGateway;
    private final PaymentEventPublishService paymentEventPublishService;
    private final PaymentMockProperties paymentMockProperties;
    private final PaymentOrderInternalProperties paymentOrderInternalProperties;

    @WriteTx
    public WalletPrepareResponse prepare(
            WalletPrepareRequest request,
            String internalSecret
    ) {
        assertInternalSecret(internalSecret);
        validatePrepareRequest(request);

        PaymentTransactionEntity transaction = paymentTransactionCreateService.getOrCreateStablePending(
                request.orderId(),
                request.orderNo(),
                request.amount(),
                paymentMockProperties.pendingTimeoutMinutes()
        );
        validatePrepareTarget(transaction);

        if (transaction.getStatus() == PaymentStatus.AUTHORIZED || transaction.getStatus() == PaymentStatus.FAILED) {
            return new WalletPrepareResponse(
                    transaction.getPaymentSessionId(),
                    null,
                    null,
                    null
            );
        }

        WalletServerGateway.WalletPrepareRequest walletPrepareRequest = new WalletServerGateway.WalletPrepareRequest(
                transaction.getOrderNo(),
                request.orderName(),
                transaction.getAmount(),
                request.successUrl(),
                request.failUrl()
        );
        String idempotencyKey = resolvePrepareIdempotencyKey(request.idempotencyKey(), transaction.getOrderNo());
        WalletServerGateway.WalletPrepareResult result = walletServerGateway.walletPrepare(walletPrepareRequest, idempotencyKey);
        LocalDateTime now = LocalDateTime.now();

        if ("FAILED".equalsIgnoreCase(result.paymentStatus())) {
            String reasonCode = DEFAULT_REASON_CODE;
            transaction.failByWebhook(
                    reasonCode,
                    result.checkoutSessionId(),
                    null,
                    now
            );
            paymentTransactionUpdateService.save(transaction);
            paymentEventPublishService.publishFailed(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    transaction.getPaymentId(),
                    reasonCode,
                    null,
                    now
            );
            return new WalletPrepareResponse(
                    coalesce(result.checkoutSessionId(), transaction.getPaymentSessionId()),
                    result.checkoutUrl(),
                    result.appLaunchUrl(),
                    result.checkoutExpiresAt()
            );
        }

        transaction.bindStablepaySession(
                transaction.getPaymentKey(),
                coalesce(result.checkoutSessionId(), transaction.getPaymentSessionId()),
                transaction.getMerchantKey(),
                transaction.getNonce(),
                transaction.getDeadline(),
                transaction.getPayloadHash(),
                transaction.getPayerAddress(),
                transaction.getTokenAddress()
        );
        paymentTransactionUpdateService.save(transaction);

        return new WalletPrepareResponse(
                transaction.getPaymentSessionId(),
                result.checkoutUrl(),
                result.appLaunchUrl(),
                result.checkoutExpiresAt()
        );
    }

    private void assertInternalSecret(String internalSecret) {
        if (internalSecret == null || !internalSecret.equals(paymentOrderInternalProperties.sharedSecret())) {
            throw new ErrorException(CommonErrorCode.UNAUTHORIZED, "내부 인증이 유효하지 않습니다.");
        }
    }

    private void validatePrepareRequest(WalletPrepareRequest request) {
        if (request == null || request.orderId() == null) {
            throw new IllegalArgumentException("order 정보가 올바르지 않습니다.");
        }
        if (request.customerId() == null || request.customerId().isBlank()) {
            throw new IllegalArgumentException("customerKey 값이 필요합니다.");
        }
        if (request.orderName() == null || request.orderName().isBlank()) {
            throw new IllegalArgumentException("orderName 값이 필요합니다.");
        }
        if (request.successUrl() == null || request.successUrl().isBlank()
                || request.failUrl() == null || request.failUrl().isBlank()) {
            throw new IllegalArgumentException("successUrl/failUrl 값이 필요합니다.");
        }
    }

    private void validatePrepareTarget(PaymentTransactionEntity transaction) {
        if (!PaymentMethod.isStable(transaction.getPaymentMethod())) {
            throw new IllegalArgumentException("STABLE 결제에 대해서만 prepare 요청이 가능합니다.");
        }
    }

    private String resolvePrepareIdempotencyKey(String idempotencyKey, String orderNo) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return "prepare:" + orderNo;
    }

    private String coalesce(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
