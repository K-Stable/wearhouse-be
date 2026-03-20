package com.wearhouse.inventory.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.inventory.domain.service.buyer.command.BuyerInventoryCommandService;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class InventoryCommandConsumer {

    private final ObjectMapper objectMapper;
    private final BuyerInventoryCommandService buyerInventoryCommandService;


    @KafkaListener(topics = "${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}")
    public void consume(
            String message,
            Acknowledgment acknowledgment,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        String eventType = "unknown";
        try {
            KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
            String eventId = envelope.eventId();
            eventType = safeEventType(envelope.eventType());
            Map<String, Object> payload = requirePayload(envelope.payload());

            if ("InventoryReserveRequested".equals(eventType)) {
                buyerInventoryCommandService.onReserveRequested(
                        eventId,
                        topic,
                        key,
                        message,
                        payload
                );
                acknowledgment.acknowledge();
                return;
            }

            if ("InventoryReleaseRequested".equals(eventType)) {
                buyerInventoryCommandService.onReleaseRequested(
                        eventId,
                        topic,
                        key,
                        message,
                        payload
                );
            }
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            throw exception;
        }
    }

    private Map<String, Object> requirePayload(Map<String, Object> payload) {
        if (payload != null) {
            return payload;
        }
        throw new IllegalArgumentException("inventory event payload 형식이 올바르지 않습니다.");
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }
}
