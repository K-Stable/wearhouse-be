package com.wearhouse.order.infra.kafka.producer;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.order.infra.jpa.repository.OrderOutboxRepository;
import com.wearhouse.order.support.monitoring.OrderKafkaFlowMetrics;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OrderOutboxRepository orderOutboxRepository;
    private final OrderKafkaFlowMetrics orderKafkaFlowMetrics;
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
        orderKafkaFlowMetrics.incrementPublishAttempt(eventType, topic, trigger);
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            orderOutboxRepository.markSuccess(eventId);
            orderKafkaFlowMetrics.incrementPublishSuccess(eventType, topic, trigger);
        } catch (Exception exception) {
            orderOutboxRepository.markFail(
                    eventId,
                    "KAFKA_SEND_ERROR",
                    exception.getMessage()
            );
            orderKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "fail");
        }
    }
}
