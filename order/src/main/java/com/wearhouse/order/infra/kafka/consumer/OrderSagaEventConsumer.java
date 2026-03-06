package com.wearhouse.order.infra.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.order.domain.order.service.command.OrderSagaCommandService;
import com.wearhouse.order.support.monitoring.OrderKafkaFlowMetrics;
import java.util.Map;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class OrderSagaEventConsumer {

    private final ObjectMapper objectMapper;
    private final OrderSagaCommandService orderSagaCommandService;
    private final OrderKafkaFlowMetrics orderKafkaFlowMetrics;

    public OrderSagaEventConsumer(
            ObjectMapper objectMapper,
            OrderSagaCommandService orderSagaCommandService,
            OrderKafkaFlowMetrics orderKafkaFlowMetrics
    ) {
        this.objectMapper = objectMapper;
        this.orderSagaCommandService = orderSagaCommandService;
        this.orderKafkaFlowMetrics = orderKafkaFlowMetrics;
    }

    @KafkaListener(topics = "${wearhouse.kafka.inventory-event-topic:wearhouse.inventory.event.v1}")
    public void consumeInventoryEvent(
            String message,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        String eventType = "unknown";
        try {
            Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() {});
            String eventId = asString(envelope.get("eventId"));
            eventType = asString(envelope.get("eventType"));
            Map<String, Object> payload = toMap(envelope.get("payload"));

            orderSagaCommandService.handleInventoryEvent(
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
            Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() {});
            String eventId = asString(envelope.get("eventId"));
            eventType = asString(envelope.get("eventType"));
            Map<String, Object> payload = toMap(envelope.get("payload"));

            orderSagaCommandService.handlePaymentEvent(
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

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("event payload 형식이 올바르지 않습니다.");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
