package com.wearhouse.order.global.kafka.service;

import com.wearhouse.order.domain.order.repository.OrderOutboxRepository;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderOutboxKafkaPublishService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderOutboxRepository orderOutboxRepository;
    private final int maxRetries;
    private final long initialDelayMs;
    private final long maxDelayMs;
    private final double delayMultiplier;
    private final long sendTimeoutMs;

    public OrderOutboxKafkaPublishService(
            KafkaTemplate<String, String> kafkaTemplate,
            OrderOutboxRepository orderOutboxRepository,
            @Value("${wearhouse.outbox.retry.max-retries:3}") int maxRetries,
            @Value("${wearhouse.outbox.retry.exponential.initial-delay-ms:2000}") long initialDelayMs,
            @Value("${wearhouse.outbox.retry.exponential.max-delay-ms:60000}") long maxDelayMs,
            @Value("${wearhouse.outbox.retry.exponential.multiplier:2.0}") double delayMultiplier,
            @Value("${wearhouse.outbox.send-timeout-ms:3000}") long sendTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.orderOutboxRepository = orderOutboxRepository;
        this.maxRetries = maxRetries;
        this.initialDelayMs = initialDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.delayMultiplier = delayMultiplier;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public void send(String eventId, String topic, String partitionKey, String payload, int currentRetryCount) {
        String key = partitionKey == null || partitionKey.isBlank() ? eventId : partitionKey;
        try {
            kafkaTemplate.send(topic, key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            orderOutboxRepository.markSuccess(eventId);
        } catch (Exception exception) {
            int nextRetryCount = currentRetryCount + 1;
            if (nextRetryCount > maxRetries) {
                orderOutboxRepository.markDead(eventId, nextRetryCount, "KAFKA_SEND_ERROR", exception.getMessage());
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
        }
    }

    private long computeDelayMillis(int retryCount) {
        double delay = initialDelayMs * Math.pow(delayMultiplier, Math.max(0, retryCount - 1));
        return (long) Math.min(delay, maxDelayMs);
    }
}
