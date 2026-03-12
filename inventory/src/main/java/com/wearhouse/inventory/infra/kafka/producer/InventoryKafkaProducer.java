package com.wearhouse.inventory.infra.kafka.producer;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.inventory.domain.repository.InventoryOutboxRepository;
import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryKafkaProducer{

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final InventoryOutboxRepository inventoryOutboxRepository;
    private final InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;
    private final OutboxProperties outboxProperties;

    public void send(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            String trigger
    ) {
        String key = partitionKey == null || partitionKey.isBlank() ? eventId : partitionKey;
        inventoryKafkaFlowMetrics.incrementPublishAttempt(eventType, topic, trigger);
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            inventoryOutboxRepository.markSuccess(eventId);
            inventoryKafkaFlowMetrics.incrementPublishSuccess(eventType, topic, trigger);
        } catch (Exception exception) {
            inventoryOutboxRepository.markFail(
                    eventId,
                    "KAFKA_SEND_ERROR",
                    exception.getMessage()
            );
            inventoryKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "fail");
        }
    }
}
