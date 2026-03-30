package com.wearhouse.inventory.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.inventory.buyer.service.BuyerInventoryCommandService;
import com.wearhouse.inventory.kafka.dto.OrderConfirmedEvent;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InventoryOrderConsumer {

    private final ObjectMapper objectMapper;
    private final BuyerInventoryCommandService buyerInventoryCommandService;


    @KafkaListener(topics = "${wearhouse.kafka.order-event-topic:wearhouse.order.event.v1}")
    public void consume(
            String message,
            Acknowledgment acknowledgment
    ) throws Exception {
        String eventType = "unknown";
        try {
            KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
            String eventId = envelope.eventId();
            eventType = safeEventType(envelope.eventType());

            if ("OrderConfirmed".equals(eventType)) {
                OrderConfirmedEvent payload = requirePayload(envelope.payload(), OrderConfirmedEvent.class);
                buyerInventoryCommandService.onOrderConfirmed(
                        eventId,
                        payload
                );
            }
            // order 확정 후 재고 차감 반영이 끝난 뒤 ack
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            throw exception;
        }
    }

    private <T> T requirePayload(Object payload, Class<T> type) {
        if (payload != null) {
            return objectMapper.convertValue(payload, type);
        }
        throw new IllegalArgumentException("order event payload 형식이 올바르지 않습니다.");
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }
}
