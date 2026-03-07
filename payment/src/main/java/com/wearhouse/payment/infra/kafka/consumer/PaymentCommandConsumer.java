package com.wearhouse.payment.infra.kafka.consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.domain.payment.service.command.PaymentCommandService;
import java.util.Map;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class PaymentCommandConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentCommandService paymentCommandService;

    public PaymentCommandConsumer(
            ObjectMapper objectMapper,
            PaymentCommandService paymentCommandService
    ) {
        this.objectMapper = objectMapper;
        this.paymentCommandService = paymentCommandService;
    }

    @KafkaListener(topics = "${wearhouse.kafka.payment-prepare-topic:wearhouse.payment.command.v1}")
    public void consume(
            String message,
            @Header(name = "kafka_receivedTopic", required = false) String topic,
            @Header(name = "kafka_receivedMessageKey", required = false) String key
    ) throws Exception {
        Map<String, Object> envelope = objectMapper.readValue(message, new TypeReference<>() {});
        String eventId = asString(envelope.get("eventId"));
        String eventType = asString(envelope.get("eventType"));
        Map<String, Object> payload = toMap(envelope.get("payload"));

        if ("PaymentPrepareRequested".equals(eventType)) {
            paymentCommandService.handlePaymentPrepareRequested(
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
        throw new IllegalArgumentException("payment event payload 형식이 올바르지 않습니다.");
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
