package com.wearhouse.order.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.order.kafka.dto.InventoryEventPayload;
import com.wearhouse.order.kafka.dto.PaymentEventPayload;
import com.wearhouse.order.saga.service.OrderSagaService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.messaging.handler.annotation.Header;
@RequiredArgsConstructor
@Component
public class OrderKafkaConsumer {

    private final ObjectMapper objectMapper;
    private final OrderSagaService orderSagaService;


    @KafkaListener(topics = "${wearhouse.kafka.inventory-event-topic:wearhouse.inventory.event.v1}")
    public void consumeInventoryEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String partitionKey,
            Acknowledgment acknowledgment
    ) throws Exception {
        processMessage(
                message,
                topic,
                partitionKey,
                acknowledgment,
                (eventId, eventType, sourceTopic, sourcePartitionKey, payload) ->
                        orderSagaService.onInventoryEvent(
                                eventId,
                                eventType,
                                sourceTopic,
                                sourcePartitionKey,
                                payload
                        ),
                InventoryEventPayload.class
        );
    }

    @KafkaListener(topics = "${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}")
    public void consumePaymentEvent(
            String message,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String partitionKey,
            Acknowledgment acknowledgment
    ) throws Exception {
        processMessage(
                message,
                topic,
                partitionKey,
                acknowledgment,
                (eventId, eventType, sourceTopic, sourcePartitionKey, payload) ->
                        orderSagaService.onPaymentEvent(
                                eventId,
                                eventType,
                                sourceTopic,
                                sourcePartitionKey,
                                payload
                        ),
                PaymentEventPayload.class
        );
    }

    private <T> void processMessage(
            String message,
            String topic,
            String partitionKey,
            Acknowledgment acknowledgment,
            SagaDispatcher<T> dispatcher,
            Class<T> payloadType
    ) throws Exception {
        KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
        String eventId = envelope.eventId();
        String eventType = safeEventType(envelope.eventType());
        T payload = requirePayload(envelope.payload(), payloadType);

        // 사가 처리(상태전이 + 이력 + 후속 이벤트)가 끝난 뒤에만 ack 한다.
        dispatcher.dispatch(
                eventId,
                eventType,
                topic,
                partitionKey,
                payload
        );
        acknowledgment.acknowledge();
    }

    private <T> T requirePayload(Object payload, Class<T> payloadType) {
        if (payload != null) {
            return objectMapper.convertValue(payload, payloadType);
        }
        throw new IllegalArgumentException("event payload 형식이 올바르지 않습니다.");
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }

    @FunctionalInterface
    private interface SagaDispatcher<T> {
        void dispatch(
                String eventId,
                String eventType,
                String topic,
                String partitionKey,
                T payload
        ) throws Exception;
    }
}
