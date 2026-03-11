package com.wearhouse.inventory.infra.kafka.service;

import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import com.wearhouse.inventory.support.config.InventoryOutboxProperties;
import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryKafkaPublishService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;
    private final InventoryOutboxProperties outboxProperties;

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
        inventoryKafkaFlowMetrics.incrementPublishAttempt(eventType, topic, trigger);
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            inventoryOutboxRepository.markSuccess(eventId);
            inventoryKafkaFlowMetrics.incrementPublishSuccess(eventType, topic, trigger);
        } catch (Exception exception) {
            int nextRetryCount = currentRetryCount + 1;
            if (nextRetryCount > outboxProperties.maxRetries()) {
                inventoryOutboxRepository.markDead(eventId, nextRetryCount, "KAFKA_SEND_ERROR", exception.getMessage());
                inventoryKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "dead");
                return;
            }

            LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(computeDelayMillis(nextRetryCount) * 1_000_000);
            inventoryOutboxRepository.markFail(
                    eventId,
                    nextRetryCount,
                    nextRetryAt,
                    "KAFKA_SEND_ERROR",
                    exception.getMessage()
            );
            inventoryKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "retry");
        }
    }

    private long computeDelayMillis(int retryCount) {
        double delay = outboxProperties.initialDelayMs() * Math.pow(outboxProperties.delayMultiplier(), Math.max(0, retryCount - 1));
        return (long) Math.min(delay, outboxProperties.maxDelayMs());
    }
}
