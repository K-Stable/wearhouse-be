package com.wearhouse.inventory.infra.kafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.inventory.domain.event.InventoryDomainEvent;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class InventoryKafkaPublishService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final long sendTimeoutMs;

    public InventoryKafkaPublishService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${wearhouse.inventory.kafka.send-timeout-ms:3000}") long sendTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public void send(InventoryDomainEvent event) {
        String payload = serialize(event);
        String key = event.getPartitionKey() == null || event.getPartitionKey().isBlank()
                ? event.getEventId()
                : event.getPartitionKey();
        try {
            kafkaTemplate.send(event.getTopic(), key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("Inventory 이벤트 Kafka 발행에 실패했습니다.", exception);
        }
    }

    private String serialize(InventoryDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event.toEnvelope());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Inventory 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}
