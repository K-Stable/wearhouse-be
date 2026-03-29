package com.wearhouse.payment.internal.service;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.lock.DistributedLock;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
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
    private static final String DEFAULT_METHOD = "CARD";
    private static final String STABLE_METHOD = "STABLE";
    private static final String LEGACY_STABLEPAY_METHOD = "STABLEPAY";

    private final PaymentInboxRepository paymentInboxRepository;
    private final PaymentTransactionCreateService paymentTransactionCreateService;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    private String paymentEventTopic;
    @Value("${wearhouse.payment.mock.pending-timeout-minutes:30}")
    private int pendingTimeoutMinutes;
    @Value("${wearhouse.payment.mock.fail-methods:FAIL}")
    private String failMethodsRaw;
    @Value("${wearhouse.payment.mock.timeout-methods:TIMEOUT}")
    private String timeoutMethodsRaw;
    private Set<String> failMethods = Set.of();
    private Set<String> timeoutMethods = Set.of();

    @PostConstruct
    public void init() {
        this.failMethods = parseUpperCaseSet(failMethodsRaw);
        this.timeoutMethods = parseUpperCaseSet(timeoutMethodsRaw);
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
            PaymentPrepareRequestedEvent payload
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
            processPrepareCommand(payload);
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

    private void processPrepareCommand(PaymentPrepareRequestedEvent event) {
        validatePrepareRequestedEvent(event);

        Optional<PaymentTransactionEntity> existing = paymentTransactionCreateService.findByOrderId(event.orderId());
        if (existing.isPresent()) {
            return;
        }

        String paymentId = PaymentIdGenerator.newPaymentId();
        LocalDateTime now = LocalDateTime.now();
        String paymentMethod = resolvePaymentMethod(event.paymentMethod());
        String normalizedMethod = normalizeMethod(paymentMethod);

        try {
            if (isStableMethod(normalizedMethod)) {
                paymentTransactionCreateService.insertPending(
                        paymentId,
                        event.orderId(),
                        event.orderNo(),
                        event.amount(),
                        paymentMethod,
                        now.plusMinutes(pendingTimeoutMinutes)
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("stablepay_pending");
                return;
            }

            if (timeoutMethods.contains(normalizedMethod)) {
                paymentTransactionCreateService.insertPending(
                        paymentId,
                        event.orderId(),
                        event.orderNo(),
                        event.amount(),
                        paymentMethod,
                        now.plusMinutes(pendingTimeoutMinutes)
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("pending_timeout");
                return;
            }

            if (failMethods.contains(normalizedMethod)) {
                paymentTransactionCreateService.insertFailed(
                        paymentId,
                        event.orderId(),
                        event.orderNo(),
                        event.amount(),
                        paymentMethod,
                        DEFAULT_REASON_CODE,
                        now
                );
                publishPaymentFailed(
                        event.orderId(),
                        event.orderNo(),
                        paymentId,
                        DEFAULT_REASON_CODE,
                        "결제 승인에 실패했습니다.",
                        now
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("failed");
                return;
            }

            paymentTransactionCreateService.insertAuthorized(
                    paymentId,
                    event.orderId(),
                    event.orderNo(),
                    event.amount(),
                    paymentMethod,
                    now
            );
            publishPaymentAuthorized(event.orderId(), event.orderNo(), paymentId, event.amount(), paymentMethod, now);
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

    private void validatePrepareRequestedEvent(PaymentPrepareRequestedEvent event) {
        if (event == null || event.orderId() == null) {
            throw new IllegalArgumentException("orderId 값이 필요합니다.");
        }
        if (event.amount() == null || event.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("결제 금액이 올바르지 않습니다.");
        }
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

    private String resolvePaymentMethod(String paymentMethod) {
        if (paymentMethod == null || paymentMethod.isBlank()) {
            return DEFAULT_METHOD;
        }
        return paymentMethod;
    }
}
