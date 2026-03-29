package com.wearhouse.order.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.order.saga.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class OrderKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final OrderSagaService orderSagaService;


    @KafkaListener(topics = "${wearhouse.kafka.inventory-event-topic:wearhouse.inventory.event.v1}")
    public void consumeInventoryEvent(
            String message,
            Acknowledgment acknowledgment,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        processMessage(
                message,
                acknowledgment,
                topic,
                key,
                (eventId, eventType, parsedTopic, parsedKey, rawMessage, orderId, payload) ->
                        orderSagaService.onInventoryEvent(
                                eventId,
                                eventType,
                                parsedTopic,
                                parsedKey,
                                rawMessage,
                                orderId,
                                payload
                        )
        );
    }

    @KafkaListener(topics = "${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    public void consumePaymentEvent(
            String message,
            Acknowledgment acknowledgment,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        processMessage(
                message,
                acknowledgment,
                topic,
                key,
                (eventId, eventType, parsedTopic, parsedKey, rawMessage, orderId, payload) ->
                        orderSagaService.onPaymentEvent(
                                eventId,
                                eventType,
                                parsedTopic,
                                parsedKey,
                                rawMessage,
                                orderId,
                                payload
                        )
        );
    }

    private void processMessage(
            String message,
            Acknowledgment acknowledgment,
            String topic,
            String key,
            SagaDispatcher dispatcher
    ) throws Exception {
        KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
        String eventId = envelope.eventId();
        String eventType = safeEventType(envelope.eventType());
        Map<String, Object> payload = requirePayload(envelope.payload());
        Long orderId = requireOrderId(payload);

        // 사가 처리(상태전이 + 이력 + 후속 이벤트)가 끝난 뒤에만 ack 한다.
        dispatcher.dispatch(
                eventId,
                eventType,
                topic,
                key,
                message,
                orderId,
                payload
        );
        acknowledgment.acknowledge();
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

    private Long requireOrderId(Map<String, Object> payload) {
        Object value = payload.get("orderId");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            return Long.parseLong(stringValue);
        }
        throw new IllegalArgumentException("orderId 값이 올바르지 않습니다.");
    }

    @FunctionalInterface
    private interface SagaDispatcher {
        void dispatch(
                String eventId,
                String eventType,
                String topic,
                String key,
                String message,
                Long orderId,
                Map<String, Object> payload
        ) throws Exception;
    }
}
