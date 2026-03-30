package com.wearhouse.payment.kafka.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.support.kafka.dto.KafkaMessageEnvelope;
import com.wearhouse.payment.kafka.dto.PaymentPrepareRequestedEvent;
import com.wearhouse.payment.kafka.handler.PaymentPrepareRequestedHandler;
import com.wearhouse.payment.support.config.PaymentKafkaTopicsProperties;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCommandConsumer {

    private final ObjectMapper objectMapper;
    private final PaymentPrepareRequestedHandler paymentPrepareRequestedHandler;
    private final PaymentKafkaTopicsProperties paymentKafkaTopicsProperties;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;

    @KafkaListener(topics = "${wearhouse.kafka.payment-prepare-topic:wearhouse.payment.command.v1}")
    public void consume(
            String message,
            Acknowledgment acknowledgment
    ) throws Exception {
        String topic = paymentKafkaTopicsProperties.paymentPrepareTopic();
        String eventType = "unknown";
        try {
            KafkaMessageEnvelope envelope = objectMapper.readValue(message, KafkaMessageEnvelope.class);
            String eventId = envelope.eventId();
            eventType = safeEventType(envelope.eventType());

            if ("PaymentPrepareRequested".equals(eventType)) {
                PaymentPrepareRequestedEvent payload = toPrepareRequestedEvent(envelope.payload());
                paymentPrepareRequestedHandler.handle(eventId, payload);
            }
            paymentKafkaFlowMetrics.incrementConsumerHandled("payment", eventType, topic, "success");
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            paymentKafkaFlowMetrics.incrementConsumerHandled("payment", eventType, topic, "failed");
            throw exception;
        }
    }

    private String safeEventType(String eventType) {
        return eventType == null || eventType.isBlank() ? "unknown" : eventType;
    }

    private PaymentPrepareRequestedEvent toPrepareRequestedEvent(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payment event payload 형식이 올바르지 않습니다.");
        }
        return objectMapper.convertValue(payload, PaymentPrepareRequestedEvent.class);
    }
}
