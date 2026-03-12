package com.wearhouse.payment.infra.kafka.producer;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final OutboxProperties outboxProperties;

    public void send(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            int currentRetryCount,
            String trigger
    ) {
        String key = partitionKey == null || partitionKey.isBlank() ? eventId : partitionKey;
        paymentKafkaFlowMetrics.incrementPublishAttempt(eventType, topic, trigger);
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            paymentOutboxRepository.markSuccess(eventId);
            paymentKafkaFlowMetrics.incrementPublishSuccess(eventType, topic, trigger);
        } catch (Exception exception) {
            int nextRetryCount = currentRetryCount + 1;
            if (nextRetryCount > outboxProperties.maxRetries()) {
                paymentOutboxRepository.markDead(eventId, nextRetryCount, "KAFKA_SEND_ERROR", exception.getMessage());
                paymentKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "dead");
                return;
            }

            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(computeDelayMillis(nextRetryCount) * 1_000_000);
            paymentOutboxRepository.markFail(
                    eventId,
                    nextRetryCount,
                    nextRetryAt,
                    "KAFKA_SEND_ERROR",
                    exception.getMessage()
            );
            paymentKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "retry");
        }
    }

    private long computeDelayMillis(int retryCount) {
        double delay = outboxProperties.initialDelayMs() * Math.pow(outboxProperties.delayMultiplier(), Math.max(0, retryCount - 1));
        return (long) Math.min(delay, outboxProperties.maxDelayMs());
    }
}
