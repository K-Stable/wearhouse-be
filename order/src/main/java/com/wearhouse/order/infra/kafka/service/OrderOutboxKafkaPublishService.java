package com.wearhouse.order.infra.kafka.service;

import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.support.monitoring.OrderKafkaFlowMetrics;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderOutboxKafkaPublishService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderKafkaFlowMetrics orderKafkaFlowMetrics;
    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double delayMultiplier;
    private final long sendTimeoutMs;

    public OrderOutboxKafkaPublishService(
            KafkaTemplate<String, String> kafkaTemplate,
            OrderOutboxRepository orderOutboxRepository,
            OrderKafkaFlowMetrics orderKafkaFlowMetrics,
            @Value("${wearhouse.outbox.retry.max-retries:3}") int maxRetries,
            @Value("${wearhouse.outbox.retry.exponential.initial-delay-ms:2000}") long initialDelayMs,
            @Value("${wearhouse.outbox.retry.exponential.max-delay-ms:60000}") long maxDelayMs,
            @Value("${wearhouse.outbox.retry.exponential.multiplier:2.0}") double delayMultiplier,
            @Value("${wearhouse.outbox.send-timeout-ms:3000}") long sendTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderOutboxRepository = orderOutboxRepository;
        this.orderKafkaFlowMetrics = orderKafkaFlowMetrics;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.delayMultiplier = delayMultiplier;
        this.sendTimeoutMs = sendTimeoutMs;
    }

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
        orderKafkaFlowMetrics.incrementPublishAttempt(eventType, topic, trigger);
        try {
            kafkaTemplate.send(topic, key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            orderOutboxRepository.markSuccess(eventId);
            orderKafkaFlowMetrics.incrementPublishSuccess(eventType, topic, trigger);
        } catch (Exception exception) {
            int nextRetryCount = currentRetryCount + 1;
            if (nextRetryCount > maxRetries) {
                orderOutboxRepository.markDead(eventId, nextRetryCount, "KAFKA_SEND_ERROR", exception.getMessage());
                orderKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "dead");
                return;
            }

            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(computeDelayMillis(nextRetryCount) * 1_000_000);
            orderOutboxRepository.markFail(
                    eventId,
                    nextRetryCount,
                    nextRetryAt,
                    "KAFKA_SEND_ERROR",
                    exception.getMessage()
            );
            orderKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "retry");
        }
    }

    private long computeDelayMillis(int retryCount) {
        double delay = initialDelayMs * Math.pow(delayMultiplier, Math.max(0, retryCount - 1));
        return (long) Math.min(delay, maxDelayMs);
    }
}
