package com.wearhouse.order.infra.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.order.domain.service.command.OrderSagaService;
import com.wearhouse.order.support.monitoring.OrderKafkaFlowMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class OrderKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final OrderSagaService orderSagaService;
    private final OrderKafkaFlowMetrics orderKafkaFlowMetrics;


    @KafkaListener(topics = "${wearhouse.kafka.inventory-event-topic:wearhouse.inventory.event.v1}")
    public void consumeInventoryEvent(
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

            orderSagaService.onInventoryEvent(
                    eventId,
                    eventType,
                    topic,
                    key,
                    message,
                    payload
            );
            orderKafkaFlowMetrics.incrementConsumerHandled("inventory", eventType, topic, "success");
        } catch (Exception exception) {
            orderKafkaFlowMetrics.incrementConsumerHandled("inventory", eventType, topic, "failed");
            throw exception;
        }
    }

    @KafkaListener(topics = "${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    public void consumePaymentEvent(
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

            orderSagaService.onPaymentEvent(
                    eventId,
                    eventType,
                    topic,
                    key,
                    message,
                    payload
            );
            orderKafkaFlowMetrics.incrementConsumerHandled("payment", eventType, topic, "success");
        } catch (Exception exception) {
            orderKafkaFlowMetrics.incrementConsumerHandled("payment", eventType, topic, "failed");
            throw exception;
        }
    }

    private Map<String, Object> requirePayload(Map<String, Object> payload) {
        if (payload != null) {
            return payload;
        }
        throw new IllegalArgumentException("event payload 형식이 올바르지 않습니다.");
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }
}
