package com.wearhouse.payment.domain.payment.service.command;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.lock.DistributedLock;
import com.wearhouse.payment.domain.payment.dto.request.PaymentConfirmRequest;
import com.wearhouse.payment.domain.payment.dto.request.WalletPrepareRequest;
import com.wearhouse.payment.domain.payment.dto.response.PaymentConfirmResponse;
import com.wearhouse.payment.domain.payment.dto.response.WalletPrepareResponse;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.domain.payment.model.PaymentStatus;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.infra.pay.WalletServerGateway;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private static final String PAYMENT_COMMAND_CONSUMER = "payment-command-consumer";
    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";
    private static final String TIMEOUT_REASON_CODE = "PAYMENT_TIMEOUT";
    private static final String STABLE_METHOD = "STABLE";
    private static final String LEGACY_STABLEPAY_METHOD = "STABLEPAY";
    private static final String CONFIRM_IDEMPOTENCY_PREFIX = "confirm:";

    private final PaymentInboxRepository paymentInboxRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final WalletServerGateway walletServerGateway;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    private String paymentEventTopic;
    @Value("${wearhouse.payment.mock.pending-timeout-minutes:30}")
    private int pendingTimeoutMinutes;
    @Value("${wearhouse.payment.mock.timeout-batch-size:200}")
    private int timeoutBatchSize;
    @Value("${wearhouse.payment.mock.fail-methods:FAIL}")
    private String failMethodsRaw;
    @Value("${wearhouse.payment.mock.timeout-methods:TIMEOUT}")
    private String timeoutMethodsRaw;
    @Value("${wearhouse.order.internal.shared-secret:wearhouse-order-internal-secret}")
    private String internalSharedSecret;
    private Set<String> failMethods = Set.of();
    private Set<String> timeoutMethods = Set.of();

    @PostConstruct
    void init() {
        this.failMethods = parseUpperCaseSet(failMethodsRaw);
        this.timeoutMethods = parseUpperCaseSet(timeoutMethodsRaw);
    }

    @WriteTx
    public PaymentConfirmResponse confirmStablepayPayment(
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

    @WriteTx
    public WalletPrepareResponse walletPrepare(
            WalletPrepareRequest request,
            String internalSecret
    ) {
        assertInternalSecret(internalSecret);
        validatePrepareRequest(request);

        PaymentTransactionEntity transaction = loadOrCreateStablePendingTransaction(request);
        validatePrepareTarget(transaction);

        if (transaction.getStatus() == PaymentStatus.AUTHORIZED) {
            return new WalletPrepareResponse(
                    transaction.getPaymentSessionId(),
                    null,
                    null,
                    null
            );
        }
        if (transaction.getStatus() == PaymentStatus.FAILED) {
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
            String reasonCode = resolveReasonCode(null);
            transaction.failByWebhook(
                    reasonCode,
                    result.checkoutSessionId(),
                    null,
                    now
            );
            paymentTransactionRepository.save(transaction);
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
        paymentTransactionRepository.save(transaction);

        return new WalletPrepareResponse(
                transaction.getPaymentSessionId(),
                result.checkoutUrl(),
                result.appLaunchUrl(),
                result.checkoutExpiresAt()
        );
    }

    private PaymentTransactionEntity loadOrCreateStablePendingTransaction(WalletPrepareRequest request) {
        Optional<PaymentTransactionEntity> existing = paymentTransactionRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return existing.get();
        }

        try {
            paymentTransactionRepository.insertPending(
                    PaymentIdGenerator.newPaymentId(),
                    request.orderId(),
                    request.orderNo(),
                    request.amount(),
                    STABLE_METHOD,
                    LocalDateTime.now().plusMinutes(pendingTimeoutMinutes)
            );
        } catch (DuplicateKeyException ignored) {
            // 동시 요청에서 중복 생성된 경우, 아래 재조회로 진행한다.
        }

        return paymentTransactionRepository.findByOrderId(request.orderId())
                .orElseThrow(() -> new IllegalArgumentException("해당 주문의 결제 정보를 찾을 수 없습니다."));
    }

    @WriteTx
    @DistributedLock(
            key = "#p4['orderId']",
            prefix = "payment:lock:prepare:"
    )
    public void handlePaymentPrepareRequested(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            Map<String, Object> payload
    ) {
        boolean received = paymentInboxRepository.tryReceive(
                eventId,
                PAYMENT_COMMAND_CONSUMER,
                "PaymentPrepareRequested",
                topic,
                partitionKey,
                rawPayload
        );
        if (!received) {
            return;
        }

        try {
            PaymentPrepareCommand command = toPrepareCommand(payload);
            processPrepareCommand(command);
            paymentInboxRepository.markProcessed(eventId, PAYMENT_COMMAND_CONSUMER);
        } catch (Exception exception) {
            paymentInboxRepository.markFailed(
                    eventId,
                    PAYMENT_COMMAND_CONSUMER,
                    "CONSUME_FAIL",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @WriteTx
    public int failExpiredPendingPayments() {
        int failedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (PaymentTransactionEntity candidate : paymentTransactionRepository.findTimeoutCandidates(now, timeoutBatchSize)) {
            int updated = paymentTransactionRepository.markFailedIfPending(
                    candidate.getOrderId(),
                    TIMEOUT_REASON_CODE,
                    now
            );
            if (updated > 0) {
                failedCount++;
                publishPaymentFailed(
                        candidate.getOrderId(),
                        candidate.getOrderNo(),
                        candidate.getPaymentId(),
                        TIMEOUT_REASON_CODE,
                        "결제 대기 시간이 만료되었습니다.",
                        now
                );
            }
        }
        paymentKafkaFlowMetrics.incrementTimeoutFailed(failedCount);
        return failedCount;
    }

    private void processPrepareCommand(PaymentPrepareCommand command) {
        Optional<PaymentTransactionEntity> existing = paymentTransactionRepository.findByOrderId(command.orderId());
        if (existing.isPresent()) {
            return;
        }

        String paymentId = PaymentIdGenerator.newPaymentId();
        LocalDateTime now = LocalDateTime.now();
        String normalizedMethod = normalizeMethod(command.paymentMethod());

        try {
            if (isStableMethod(normalizedMethod)) {
                paymentTransactionRepository.insertPending(
                        paymentId,
                        command.orderId(),
                        command.orderNo(),
                        command.amount(),
                        command.paymentMethod(),
                        now.plusMinutes(pendingTimeoutMinutes)
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("stablepay_pending");
                return;
            }

            if (timeoutMethods.contains(normalizedMethod)) {
                paymentTransactionRepository.insertPending(
                        paymentId,
                        command.orderId(),
                        command.orderNo(),
                        command.amount(),
                        command.paymentMethod(),
                        now.plusMinutes(pendingTimeoutMinutes)
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("pending_timeout");
                return;
            }

            if (failMethods.contains(normalizedMethod)) {
                paymentTransactionRepository.insertFailed(
                        paymentId,
                        command.orderId(),
                        command.orderNo(),
                        command.amount(),
                        command.paymentMethod(),
                        DEFAULT_REASON_CODE,
                        now
                );
                publishPaymentFailed(
                        command.orderId(),
                        command.orderNo(),
                        paymentId,
                        DEFAULT_REASON_CODE,
                        "결제 승인에 실패했습니다.",
                        now
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("failed");
                return;
            }

            paymentTransactionRepository.insertAuthorized(
                    paymentId,
                    command.orderId(),
                    command.orderNo(),
                    command.amount(),
                    command.paymentMethod(),
                    now
            );
            publishPaymentAuthorized(command.orderId(), command.orderNo(), paymentId, command.amount(), command.paymentMethod(), now);
            paymentKafkaFlowMetrics.incrementPaymentDecision("authorized");
        } catch (DuplicateKeyException ignored) {
            // order_id unique 충돌은 중복 요청으로 간주한다.
        }
    }

    private void publishPaymentAuthorized(
            Long orderId,
            String orderNo,
            String paymentId,
            BigDecimal amount,
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

    private PaymentPrepareCommand toPrepareCommand(Map<String, Object> payload) {
        Long orderId = asLong(payload.get("orderId"));
        if (orderId == null) {
            throw new IllegalArgumentException("orderId 값이 필요합니다.");
        }

        BigDecimal amount = asBigDecimal(payload.get("amount"));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }

        String orderNo = asString(payload.get("orderNo"));
        String paymentMethod = asString(payload.get("paymentMethod"));
        if (paymentMethod == null || paymentMethod.isBlank()) {
            paymentMethod = "CARD";
        }

        return new PaymentPrepareCommand(orderId, orderNo, amount, paymentMethod);
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
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
        if (request.paymentKey() == null || request.paymentKey().isBlank()) {
            throw new IllegalArgumentException("paymentKey 값이 필요합니다.");
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

    private void validatePrepareTarget(PaymentTransactionEntity transaction) {
        if (!isStableMethod(normalizeMethod(transaction.getPaymentMethod()))) {
            throw new IllegalArgumentException("STABLE 결제에 대해서만 prepare 요청이 가능합니다.");
        }
    }

    private String resolvePaymentId(String candidate, String fallback) {
        if (candidate != null && !candidate.isBlank()) {
            return candidate;
        }
        return fallback;
    }

    private String resolveReasonCode(String reasonCode) {
        if (reasonCode == null || reasonCode.isBlank()) {
            return DEFAULT_REASON_CODE;
        }
        return reasonCode;
    }

    private String resolvePrepareIdempotencyKey(String idempotencyKey, String orderNo) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            return idempotencyKey;
        }
        return "prepare:" + orderNo;
    }

    private String resolveConfirmIdempotencyKey(String orderNo, String paymentKey) {
        return CONFIRM_IDEMPOTENCY_PREFIX + orderNo + ":" + paymentKey;
    }

    private Set<String> parseUpperCaseSet(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(String::toUpperCase)
                .collect(Collectors.toSet());
    }

    private String normalizeMethod(String method) {
        return method == null ? "" : method.trim().toUpperCase();
    }

    private boolean isStableMethod(String normalizedMethod) {
        return STABLE_METHOD.equals(normalizedMethod) || LEGACY_STABLEPAY_METHOD.equals(normalizedMethod);
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

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return Long.parseLong(stringValue);
        }
        return null;
    }

    private BigDecimal asBigDecimal(Object value) {
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number));
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return new BigDecimal(stringValue);
        }
        return null;
    }

    private record PaymentPrepareCommand(
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod
    ) {
    }
}
