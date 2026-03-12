package com.wearhouse.inventory.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.inventory.domain.service.command.InventoryCommandService;
import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryOrderConsumer {

    private final ObjectMapper objectMapper;
    private final InventoryCommandService inventoryCommandService;
    private final InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;


    @KafkaListener(topics = "${wearhouse.kafka.order-event-topic:wearhouse.order.event.v1}")
    public void consume(
            String message,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        String eventType = "unknown";
        try {
            KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
            String eventId = envelope.eventId();
            eventType = safeEventType(envelope.eventType());
            Map<String, Object> payload = requirePayload(envelope.payload());

            if ("OrderConfirmed".equals(eventType)) {
                inventoryCommandService.onOrderConfirmed(
                        eventId,
                        topic,
                        key,
                        message,
                        payload
                );
            }
            inventoryKafkaFlowMetrics.incrementConsumerHandled("order", eventType, topic, "success");
        } catch (Exception exception) {
            inventoryKafkaFlowMetrics.incrementConsumerHandled("order", eventType, topic, "failed");
            throw exception;
        }
    }

    private Map<String, Object> requirePayload(Map<String, Object> payload) {
        if (payload != null) {
            return payload;
        }
        throw new IllegalArgumentException("order event payload 형식이 올바르지 않습니다.");
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }
}
