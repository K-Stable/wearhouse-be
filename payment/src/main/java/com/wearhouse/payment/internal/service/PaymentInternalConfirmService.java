package com.wearhouse.payment.internal.service;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.internal.dto.request.PaymentConfirmRequest;
import com.wearhouse.payment.internal.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.stablepay.client.WalletServerGateway;
import com.wearhouse.payment.support.PaymentIdGenerator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentInternalConfirmService {

    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";
    private static final String CONFIRM_IDEMPOTENCY_PREFIX = "confirm:";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WalletServerGateway walletServerGateway;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    private String paymentEventTopic;
    @Value("${wearhouse.order.internal.shared-secret:wearhouse-order-internal-secret}")
    private String internalSharedSecret;

    @WriteTx
    public PaymentConfirmResponse confirm(
            PaymentConfirmRequest request,
            String internalSecret
    ) {
        assertInternalSecret(internalSecret);
        validateConfirmRequest(request);

        PaymentTransactionEntity transaction = paymentTransactionRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문의 결제 정보를 찾을 수 없습니다."));
        validateConfirmTarget(transaction, request);

        if (transaction.getStatus() == PaymentStatus.AUTHORIZED) {
            return new PaymentConfirmResponse(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    transaction.getPaymentId(),
                    PaymentStatus.AUTHORIZED.name(),
                    transaction.getCommandStatus(),
                    null
            );
        }
        if (transaction.getStatus() == PaymentStatus.FAILED) {
            return new PaymentConfirmResponse(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    transaction.getPaymentId(),
                    PaymentStatus.FAILED.name(),
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
            paymentTransactionRepository.save(transaction);
            publishPaymentAuthorized(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    resolvePaymentId(result.paymentId(), transaction.getPaymentId()),
                    transaction.getAmount(),
                    transaction.getPaymentMethod(),
                    now
            );
            return new PaymentConfirmResponse(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    transaction.getPaymentId(),
                    PaymentStatus.AUTHORIZED.name(),
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
            paymentTransactionRepository.save(transaction);
            publishPaymentFailed(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    resolvePaymentId(result.paymentId(), transaction.getPaymentId()),
                    reasonCode,
                    "결제 승인에 실패했습니다.",
                    now
            );
            return new PaymentConfirmResponse(
                    transaction.getOrderId(),
                    transaction.getOrderNo(),
                    transaction.getPaymentId(),
                    PaymentStatus.FAILED.name(),
                    result.commandStatus(),
                    reasonCode
            );
        }

        return new PaymentConfirmResponse(
                transaction.getOrderId(),
                transaction.getOrderNo(),
                transaction.getPaymentId(),
                PaymentStatus.PENDING.name(),
                result.commandStatus(),
                null
        );
    }

    private void publishPaymentAuthorized(
            Long orderId,
            String orderNo,
            String paymentId,
            java.math.BigDecimal amount,
            String paymentMethod,
            LocalDateTime authorizedAt
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("paymentId", paymentId);
        payload.put("amount", amount);
        payload.put("method", paymentMethod);
        payload.put("authorizedAt", authorizedAt);

        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventId(PaymentIdGenerator.newEventId())
                .eventType("PaymentAuthorized")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(orderId))
                .topic(paymentEventTopic)
                .partitionKey(String.valueOf(orderId))
                .payload(payload)
                .build();
        paymentDomainEventPublisher.publish(event);
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
                .topic(paymentEventTopic)
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
