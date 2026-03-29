package com.wearhouse.payment.transaction.service;

import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.payment.domain.payment.entity.PaymentTransactionEntity;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEventPublisher;
import com.wearhouse.payment.infra.jpa.repository.PaymentTransactionRepository;
import com.wearhouse.payment.support.PaymentIdGenerator;
import com.wearhouse.payment.support.config.PaymentKafkaTopicsProperties;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentPendingTimeoutService {

    private static final String TIMEOUT_REASON_CODE = "PAYMENT_TIMEOUT";

    private final PaymentTransactionRepository paymentTransactionRepository;
    private final PaymentTransactionUpdateService paymentTransactionUpdateService;
    private final PaymentDomainEventPublisher paymentDomainEventPublisher;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final PaymentKafkaTopicsProperties paymentKafkaTopicsProperties;
    @Value("${wearhouse.payment.mock.timeout-batch-size:200}")
    private int timeoutBatchSize;

    @WriteTx
    public int failExpiredPendingPayments() {
        int failedCount = 0;
        LocalDateTime now = LocalDateTime.now();
        for (PaymentTransactionEntity candidate : paymentTransactionRepository.findTimeoutCandidates(now, timeoutBatchSize)) {
            int updated = paymentTransactionUpdateService.markFailedIfPending(
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
}
