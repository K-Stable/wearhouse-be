package com.wearhouse.payment.domain.payment.service.command;

import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import com.wearhouse.payment.support.PaymentIdGenerator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;

@Service
public class PaymentCommandService {

    private static final String PAYMENT_COMMAND_CONSUMER = "payment-command-consumer";
    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";
    private static final String TIMEOUT_REASON_CODE = "PAYMENT_TIMEOUT";

    private final PaymentInboxRepository paymentInboxRepository;
    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final String paymentEventTopic;
    private final int pendingTimeoutMinutes;
    private final int timeoutBatchSize;
    private final Set<String> failMethods;
    private final Set<String> timeoutMethods;

    public PaymentCommandService(
            PaymentInboxRepository paymentInboxRepository,
            PaymentTransactionRepository paymentTransactionRepository,
            PaymentDomainEventPublisher paymentDomainEventPublisher,
            PaymentKafkaFlowMetrics paymentKafkaFlowMetrics,
            @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}") String paymentEventTopic,
            @Value("${wearhouse.payment.mock.pending-timeout-minutes:30}") int pendingTimeoutMinutes,
            @Value("${wearhouse.payment.mock.timeout-batch-size:200}") int timeoutBatchSize,
            @Value("${wearhouse.payment.mock.fail-methods:FAIL}") String failMethods,
            @Value("${wearhouse.payment.mock.timeout-methods:TIMEOUT}") String timeoutMethods
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
    }

    @WriteTx
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

    private record PaymentPrepareCommand(
            Long orderId,
            String orderNo,
            BigDecimal amount,
            String paymentMethod
    ) {
    }
}
