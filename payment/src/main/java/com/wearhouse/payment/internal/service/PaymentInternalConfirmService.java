package com.wearhouse.payment.internal.service;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.internal.dto.request.PaymentConfirmRequest;
import com.wearhouse.payment.internal.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.internal.mapper.PaymentInternalResponseMapper;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.support.config.PaymentOrderInternalProperties;
import com.wearhouse.payment.stablepay.client.WalletServerGateway;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import com.wearhouse.payment.transaction.service.PaymentTransactionUpdateService;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalConfirmService {

    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";
    private static final String CONFIRM_IDEMPOTENCY_PREFIX = "confirm:";

    private final PaymentTransactionCreateService paymentTransactionCreateService;
    private final PaymentTransactionUpdateService paymentTransactionUpdateService;
    private final WalletServerGateway walletServerGateway;
    private final PaymentEventPublishService paymentEventPublishService;
    private final PaymentOrderInternalProperties paymentOrderInternalProperties;
    private final PaymentInternalResponseMapper paymentInternalResponseMapper;

    @WriteTx
    public PaymentConfirmResponse confirm(
            PaymentConfirmRequest request,
            String internalSecret
    ) {
        assertInternalSecret(internalSecret);
        validateConfirmRequest(request);

        PaymentTransactionEntity transaction = paymentTransactionCreateService.findByOrderId(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문의 결제 정보를 찾을 수 없습니다."));
        validateConfirmTarget(transaction, request);

        if (transaction.getStatus() == PaymentStatus.AUTHORIZED) {
            return paymentInternalResponseMapper.toConfirmResponse(
                    transaction,
                    PaymentStatus.AUTHORIZED,
                    transaction.getCommandStatus(),
                    null
            );
        }
        if (transaction.getStatus() == PaymentStatus.FAILED) {
            return paymentInternalResponseMapper.toConfirmResponse(
                    transaction,
                    PaymentStatus.FAILED,
                    transaction.getCommandStatus(),
                    transaction.getReasonCode()
            );
        }

        String idempotencyKey = resolveConfirmIdempotencyKey(transaction.getOrderNo(), request.paymentKey());
        WalletServerGateway.WalletConfirmRequest walletConfirmRequest = new WalletServerGateway.WalletConfirmRequest(
                transaction.getOrderNo(),
                request.paymentKey(),
                request.amount()
        );
        WalletServerGateway.WalletConfirmResult result = walletServerGateway.walletConfirm(walletConfirmRequest, idempotencyKey);
        LocalDateTime now = LocalDateTime.now();
        if (result.resultType() == WalletServerGateway.ResultType.AUTHORIZED) {
            transaction.bindPaymentKey(request.paymentKey());
            transaction.authorizeByWebhook(
                    result.txHash(),
                    result.commandId(),
                    result.commandStatus(),
                    now
            );
            paymentTransactionUpdateService.save(transaction);
            paymentEventPublishService.publishAuthorized(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    resolvePaymentId(result.paymentId(), transaction.getPaymentId()),
                    transaction.getAmount(),
                    transaction.getPaymentMethod(),
                    now
            );
            return paymentInternalResponseMapper.toConfirmResponse(
                    transaction,
                    PaymentStatus.AUTHORIZED,
                    result.commandStatus(),
                    null
            );
        }

        if (result.resultType() == WalletServerGateway.ResultType.FAILED) {
            String reasonCode = resolveReasonCode(result.reasonCode());
            transaction.failByWebhook(
                    reasonCode,
                    result.commandId(),
                    result.commandStatus(),
                    now
            );
            paymentTransactionUpdateService.save(transaction);
            paymentEventPublishService.publishFailed(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    resolvePaymentId(result.paymentId(), transaction.getPaymentId()),
                    reasonCode,
                    "결제 승인에 실패했습니다.",
                    now
            );
            return paymentInternalResponseMapper.toConfirmResponse(
                    transaction,
                    PaymentStatus.FAILED,
                    result.commandStatus(),
                    reasonCode
            );
        }

        return paymentInternalResponseMapper.toConfirmResponse(
                transaction,
                PaymentStatus.PENDING,
                result.commandStatus(),
                null
        );
    }

    private void assertInternalSecret(String internalSecret) {
        if (internalSecret == null || !internalSecret.equals(paymentOrderInternalProperties.sharedSecret())) {
            throw new ErrorException(CommonErrorCode.UNAUTHORIZED, "내부 인증이 유효하지 않습니다.");
        }
    }

    private void validateConfirmRequest(PaymentConfirmRequest request) {
        if (request == null || request.orderId() == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new IllegalArgumentException("order 정보가 올바르지 않습니다.");
        }
        if (request.amount() == null || request.amount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
        if (request.paymentKey() == null || request.paymentKey().isBlank()) {
            throw new IllegalArgumentException("paymentKey 값이 필요합니다.");
        }
    }

    private void validateConfirmTarget(PaymentTransactionEntity transaction, PaymentConfirmRequest request) {
        if (!request.orderNo().equals(transaction.getOrderNo())) {
            throw new IllegalArgumentException("orderNo 값이 일치하지 않습니다.");
        }
        if (transaction.getAmount() == null || transaction.getAmount().compareTo(request.amount()) != 0) {
            throw new IllegalArgumentException("결제 금액이 주문 금액과 일치하지 않습니다.");
        }
        if (transaction.getPaymentKey() != null
                && !transaction.getPaymentKey().isBlank()
                && !transaction.getPaymentKey().equals(request.paymentKey())) {
            throw new IllegalArgumentException("paymentKey 값이 기존 결제 정보와 일치하지 않습니다.");
        }
    }

    private String resolveConfirmIdempotencyKey(String orderNo, String paymentKey) {
        return CONFIRM_IDEMPOTENCY_PREFIX + orderNo + ":" + paymentKey;
    }

    private String resolveReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return DEFAULT_REASON_CODE;
        }
        return reasonCode;
    }

    private String resolvePaymentId(String candidate, String fallback) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return fallback;
    }
}
