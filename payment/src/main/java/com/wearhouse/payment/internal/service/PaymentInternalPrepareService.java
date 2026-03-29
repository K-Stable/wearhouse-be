package com.wearhouse.payment.internal.service;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.internal.dto.request.WalletPrepareRequest;
import com.wearhouse.payment.internal.dto.response.WalletPrepareResponse;
import com.wearhouse.payment.stablepay.client.WalletServerGateway;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.config.PaymentKafkaTopicsProperties;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import com.wearhouse.payment.transaction.service.PaymentTransactionUpdateService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalPrepareService {

    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";
    private static final String LEGACY_STABLEPAY_METHOD = "STABLEPAY";

    private static final String STABLE_METHOD = "STABLE";

    private final PaymentTransactionCreateService paymentTransactionCreateService;
    private final PaymentTransactionUpdateService paymentTransactionUpdateService;
    private final WalletServerGateway walletServerGateway;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaTopicsProperties paymentKafkaTopicsProperties;
    @Value("${wearhouse.payment.mock.pending-timeout-minutes:30}")
    private int pendingTimeoutMinutes;
    @Value("${wearhouse.order.internal.shared-secret:wearhouse-order-internal-secret}")
    private String internalSharedSecret;

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
                pendingTimeoutMinutes
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

        String paymentStatus = normalizePrepareStatus(result.paymentStatus());
        if ("FAILED".equals(paymentStatus)) {
            String reasonCode = DEFAULT_REASON_CODE;
            transaction.failByWebhook(
                    reasonCode,
                    result.checkoutSessionId(),
                    null,
                    now
            );
            paymentTransactionUpdateService.save(transaction);
            publishPaymentFailed(
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

    private void publishPaymentFailed(
            Long orderId,
            String orderNo,
            String paymentId,
            String reasonCode,
            String reasonMessage,
            LocalDateTime failedAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("paymentId", paymentId);
        payload.put("reasonCode", reasonCode);
        payload.put("reasonMessage", reasonMessage);
        payload.put("failedAt", failedAt);

        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventId(PaymentIdGenerator.newEventId())
                .eventType("PaymentFailed")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(orderId))
                .topic(paymentKafkaTopicsProperties.getPaymentEventTopic())
                .partitionKey(String.valueOf(orderId))
                .payload(payload)
                .build();
        paymentDomainEventPublisher.publish(event);
    }

    private void assertInternalSecret(String internalSecret) {
        if (internalSecret == null || !internalSecret.equals(internalSharedSecret)) {
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
        if (!isStableMethod(normalizeMethod(transaction.getPaymentMethod()))) {
            throw new IllegalArgumentException("STABLE 결제에 대해서만 prepare 요청이 가능합니다.");
        }
    }

    private String resolvePrepareIdempotencyKey(String idempotencyKey, String orderNo) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return "prepare:" + orderNo;
    }

    private boolean isStableMethod(String normalizedMethod) {
        return STABLE_METHOD.equals(normalizedMethod) || LEGACY_STABLEPAY_METHOD.equals(normalizedMethod);
    }

    private String normalizeMethod(String method) {
        return method == null ? "" : method.trim().toUpperCase();
    }

    private String normalizePrepareStatus(String status) {
        if (status == null || status.isBlank()) {
            return "READY";
        }
        return status.trim().toUpperCase();
    }

    private String coalesce(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }
}
