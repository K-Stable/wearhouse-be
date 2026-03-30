package com.wearhouse.inventory.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.inventory.buyer.service.BuyerInventoryCommandService;
import com.wearhouse.inventory.kafka.dto.InventoryReleaseRequestedEvent;
import com.wearhouse.inventory.kafka.dto.InventoryReserveRequestedEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class InventoryCommandConsumer {

    private final ObjectMapper objectMapper;
    private final BuyerInventoryCommandService buyerInventoryCommandService;


    @KafkaListener(topics = "${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}")
    public void consume(
            String message,
            Acknowledgment acknowledgment
    ) throws Exception {
        String eventType = "unknown";
        try {
            KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
            String eventId = envelope.eventId();
            eventType = safeEventType(envelope.eventType());

            if ("InventoryReserveRequested".equals(eventType)) {
                InventoryReserveRequestedEvent payload = requirePayload(envelope.payload(), InventoryReserveRequestedEvent.class);
                buyerInventoryCommandService.onReserveRequested(
                        eventId,
                        payload
                );
                // reserve 처리 완료 이후 ack
                acknowledgment.acknowledge();
                return;
            }

            if ("InventoryReleaseRequested".equals(eventType)) {
                InventoryReleaseRequestedEvent payload = requirePayload(envelope.payload(), InventoryReleaseRequestedEvent.class);
                buyerInventoryCommandService.onReleaseRequested(
                        eventId,
                        payload
                );
            }
            // release 또는 무시 이벤트도 파싱/핸들링 후 ack
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            throw exception;
        }
    }

    private <T> T requirePayload(Object payload, Class<T> type) {
        if (payload != null) {
            return objectMapper.convertValue(payload, type);
        }
        throw new IllegalArgumentException("inventory event payload 형식이 올바르지 않습니다.");
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }
}
