package com.wearhouse.payment.domain.payment.service.command;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.lock.DistributedLock;
import com.wearhouse.payment.domain.payment.dto.request.StablepaySessionPrepareRequest;
import com.wearhouse.payment.domain.payment.dto.response.StablepaySessionPrepareResponse;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class PaymentCommandService {

    private static final String PAYMENT_COMMAND_CONSUMER = "payment-command-consumer";
    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";
    private static final String TIMEOUT_REASON_CODE = "PAYMENT_TIMEOUT";
    private static final String STABLEPAY_METHOD = "STABLEPAY";

    private final PaymentInboxRepository paymentInboxRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final String paymentEventTopic;
    private final int pendingTimeoutMinutes;
    private final int timeoutBatchSize;
    private final Set<String> failMethods;
    private final Set<String> timeoutMethods;
    private final String internalSharedSecret;
    private final int stablepaySessionExpireMinutes;
    private final String stablepayMerchantKey;

    public PaymentCommandService(
            PaymentInboxRepository paymentInboxRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentDomainEventPublisher paymentDomainEventPublisher,
            PaymentKafkaFlowMetrics paymentKafkaFlowMetrics,
            @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}") String paymentEventTopic,
            @Value("${wearhouse.payment.mock.pending-timeout-minutes:30}") int pendingTimeoutMinutes,
            @Value("${wearhouse.payment.mock.timeout-batch-size:200}") int timeoutBatchSize,
            @Value("${wearhouse.payment.mock.fail-methods:FAIL}") String failMethods,
            @Value("${wearhouse.payment.mock.timeout-methods:TIMEOUT}") String timeoutMethods,
            @Value("${wearhouse.order.internal.shared-secret:wearhouse-order-internal-secret}") String internalSharedSecret,
            @Value("${wearhouse.payment.stablepay.session-expire-minutes:15}") int stablepaySessionExpireMinutes,
            @Value("${wearhouse.payment.stablepay.merchant-key:merchant_demo_key}") String stablepayMerchantKey
    ) {
        this.paymentInboxRepository = paymentInboxRepository;
        this.paymentTransactionRepository = paymentTransactionRepository;
        this.paymentDomainEventPublisher = paymentDomainEventPublisher;
        this.paymentKafkaFlowMetrics = paymentKafkaFlowMetrics;
        this.paymentEventTopic = paymentEventTopic;
        this.pendingTimeoutMinutes = pendingTimeoutMinutes;
        this.timeoutBatchSize = timeoutBatchSize;
        this.failMethods = parseUpperCaseSet(failMethods);
        this.timeoutMethods = parseUpperCaseSet(timeoutMethods);
        this.internalSharedSecret = internalSharedSecret;
        this.stablepaySessionExpireMinutes = stablepaySessionExpireMinutes;
        this.stablepayMerchantKey = stablepayMerchantKey;
    }

    @WriteTx
    public StablepaySessionPrepareResponse prepareStablepaySession(
            StablepaySessionPrepareRequest request,
            String internalSecret
    ) {
        assertInternalSecret(internalSecret);
        validatePrepareSessionRequest(request);

        Optional<PaymentTransactionEntity> existing = paymentTransactionRepository.findByOrderId(request.orderId());
        if (existing.isPresent()) {
            return toSessionResponse(existing.get());
        }

        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(stablepaySessionExpireMinutes);
        StablepaySessionIssued sessionIssued = issueSession(request, expiresAt);

        try {
            PaymentTransactionEntity transaction = PaymentTransactionEntity.pending(
                    sessionIssued.paymentId(),
                    request.orderId(),
                    request.orderNo(),
                    request.amount(),
                    STABLEPAY_METHOD,
                    expiresAt
            );
            transaction.bindStablepaySession(
                    sessionIssued.paymentKey(),
                    sessionIssued.paymentSessionId(),
                    sessionIssued.merchantKey(),
                    request.payerAddress(),
                    request.tokenAddress()
            );
            paymentTransactionRepository.save(transaction);
            return toSessionResponse(transaction, sessionIssued.nonce(), sessionIssued.deadline(), sessionIssued.payloadHash());
        } catch (DuplicateKeyException ignored) {
            PaymentTransactionEntity duplicated = paymentTransactionRepository.findByOrderId(request.orderId())
                    .orElseThrow(() -> new IllegalStateException("중복 결제 트랜잭션 조회에 실패했습니다."));
            return toSessionResponse(duplicated);
        }
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
            if (STABLEPAY_METHOD.equals(normalizedMethod)) {
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
            publishPaymentAuthorized(command, paymentId, now);
            paymentKafkaFlowMetrics.incrementPaymentDecision("authorized");
        } catch (DuplicateKeyException ignored) {
            // order_id unique 충돌은 중복 요청으로 간주한다.
        }
    }

    private void publishPaymentAuthorized(PaymentPrepareCommand command, String paymentId, LocalDateTime authorizedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", command.orderId());
        payload.put("orderNo", command.orderNo());
        payload.put("paymentId", paymentId);
        payload.put("amount", command.amount());
        payload.put("method", command.paymentMethod());
        payload.put("authorizedAt", authorizedAt);

        PaymentDomainEvent event = PaymentDomainEvent.builder()
                .eventId(PaymentIdGenerator.newEventId())
                .eventType("PaymentAuthorized")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(command.orderId()))
                .topic(paymentEventTopic)
                .partitionKey(String.valueOf(command.orderId()))
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

    private void validatePrepareSessionRequest(StablepaySessionPrepareRequest request) {
        if (request == null || request.orderId() == null || request.orderNo() == null || request.orderNo().isBlank()) {
            throw new IllegalArgumentException("order 정보가 올바르지 않습니다.");
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
        if (request.payerAddress() == null || request.payerAddress().isBlank()) {
            throw new IllegalArgumentException("payerAddress 값이 필요합니다.");
        }
        if (request.tokenAddress() == null || request.tokenAddress().isBlank()) {
            throw new IllegalArgumentException("tokenAddress 값이 필요합니다.");
        }
        if (request.chainId() == null || request.chainId().isBlank()) {
            throw new IllegalArgumentException("chainId 값이 필요합니다.");
        }
    }

    private StablepaySessionIssued issueSession(StablepaySessionPrepareRequest request, LocalDateTime expiresAt) {
        String paymentId = PaymentIdGenerator.newPaymentId();
        String paymentKey = "pay_" + PaymentIdGenerator.newEventId().substring(0, 20);
        String paymentSessionId = "ps_" + PaymentIdGenerator.newEventId().substring(0, 20);
        String nonce = "nonce_" + PaymentIdGenerator.newEventId().substring(0, 16);
        String deadline = OffsetDateTime.of(expiresAt, ZoneOffset.UTC).toString();
        String payloadHash = sha256Hex(
                request.orderId() + ":" + request.orderNo() + ":" + request.amount() + ":" + request.payerAddress() + ":" + nonce
        );
        return new StablepaySessionIssued(
                paymentId,
                paymentKey,
                paymentSessionId,
                stablepayMerchantKey,
                nonce,
                deadline,
                payloadHash
        );
    }

    private StablepaySessionPrepareResponse toSessionResponse(PaymentTransactionEntity transaction) {
        if (transaction.getPaymentSessionId() == null || transaction.getPaymentSessionId().isBlank()) {
            throw new ErrorException(CommonErrorCode.BUSINESS_RULE_VIOLATION, "StablePay 세션이 아직 준비되지 않았습니다.");
        }
        String fallbackDeadline = transaction.getExpiresAt() == null
                ? OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(stablepaySessionExpireMinutes).toString()
                : OffsetDateTime.of(transaction.getExpiresAt(), ZoneOffset.UTC).toString();
        String nonce = "nonce_" + transaction.getPaymentSessionId();
        String payloadHash = sha256Hex(transaction.getPaymentId() + ":" + transaction.getPaymentSessionId());
        return toSessionResponse(transaction, nonce, fallbackDeadline, payloadHash);
    }

    private StablepaySessionPrepareResponse toSessionResponse(
            PaymentTransactionEntity transaction,
            String nonce,
            String deadline,
            String payloadHash
    ) {
        return new StablepaySessionPrepareResponse(
                transaction.getPaymentKey(),
                transaction.getPaymentId(),
                transaction.getPaymentSessionId(),
                transaction.getMerchantKey(),
                nonce,
                deadline,
                payloadHash
        );
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

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                builder.append(String.format("%02x", current));
            }
            return builder.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("stablepay payload hash 생성에 실패했습니다.", exception);
        }
    }

    private record PaymentPrepareCommand(
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod
    ) {
    }

    private record StablepaySessionIssued(
            String paymentId,
            String paymentKey,
            String paymentSessionId,
            String merchantKey,
            String nonce,
            String deadline,
            String payloadHash
    ) {
    }
}
