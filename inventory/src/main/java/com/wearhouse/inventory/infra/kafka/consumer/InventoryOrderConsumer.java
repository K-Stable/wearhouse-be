package com.wearhouse.inventory.infra.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.inventory.domain.service.command.InventoryCommandService;
import java.util.Map;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class InventoryOrderConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryCommandService inventoryCommandService;

    public InventoryOrderConsumer(
            ObjectMapper objectMapper,
            InventoryCommandService inventoryCommandService
    ) {
        this.objectMapper = objectMapper;
        this.inventoryCommandService = inventoryCommandService;
    }

    @KafkaListener(topics = "${wearhouse.kafka.order-event-topic:wearhouse.order.event.v1}")
    public void consume(
            String message,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() {});
        String eventId = asString(envelope.get("eventId"));
        String eventType = asString(envelope.get("eventType"));
        Map<String, Object> payload = toMap(envelope.get("payload"));

        if ("OrderConfirmed".equals(eventType)) {
            inventoryCommandService.onOrderConfirmed(
                    eventId,
                    topic,
                    key,
                    message,
                    payload
            );
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("order event payload 형식이 올바르지 않습니다.");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
