package com.wearhouse.payment.internal.service;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.support.lock.DistributedLock;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.model.PaymentMethod;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import com.wearhouse.payment.kafka.publisher.PaymentEventPublishService;
import com.wearhouse.payment.infra.jpa.repository.PaymentInboxRepository;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.config.PaymentMockProperties;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import com.wearhouse.payment.transaction.service.PaymentTransactionCreateService;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentCommandService {

    private static final String PAYMENT_COMMAND_CONSUMER = "payment-command-consumer";
    private static final String DEFAULT_REASON_CODE = "PAYMENT_FAILED";

    private final PaymentInboxRepository paymentInboxRepository;
    private final PaymentTransactionCreateService paymentTransactionCreateService;
    private final PaymentEventPublishService paymentEventPublishService;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final PaymentMockProperties paymentMockProperties;
    private Set<String> failMethods = Set.of();
    private Set<String> timeoutMethods = Set.of();

    @PostConstruct
    public void init() {
        this.failMethods = parseUpperCaseSet(paymentMockProperties.failMethods());
        this.timeoutMethods = parseUpperCaseSet(paymentMockProperties.timeoutMethods());
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
        String requestedMethodToken = PaymentMethod.requestedTokenOrDefault(event.paymentMethod());
        String paymentMethod = PaymentMethod.from(requestedMethodToken).name();

        try {
            if (PaymentMethod.isStable(requestedMethodToken)) {
                paymentTransactionCreateService.insertPending(
                        paymentId,
                        event.orderId(),
                        event.orderNo(),
                        event.amount(),
                        paymentMethod,
                        now.plusMinutes(paymentMockProperties.pendingTimeoutMinutes())
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("stablepay_pending");
                return;
            }

            if (timeoutMethods.contains(requestedMethodToken)) {
                paymentTransactionCreateService.insertPending(
                        paymentId,
                        event.orderId(),
                        event.orderNo(),
                        event.amount(),
                        paymentMethod,
                        now.plusMinutes(paymentMockProperties.pendingTimeoutMinutes())
                );
                paymentKafkaFlowMetrics.incrementPaymentDecision("pending_timeout");
                return;
            }

            if (failMethods.contains(requestedMethodToken)) {
                paymentTransactionCreateService.insertFailed(
                        paymentId,
                        event.orderId(),
                        event.orderNo(),
                        event.amount(),
                        paymentMethod,
                        DEFAULT_REASON_CODE,
                        now
                );
                paymentEventPublishService.publishFailed(
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
            paymentEventPublishService.publishAuthorized(
                    event.orderId(),
                    event.orderNo(),
                    paymentId,
                    event.amount(),
                    paymentMethod,
                    now
            );
            paymentKafkaFlowMetrics.incrementPaymentDecision("authorized");
        } catch (DuplicateKeyException ignored) {
            // order_id unique 충돌은 중복 요청으로 간주한다.
        }
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
}
