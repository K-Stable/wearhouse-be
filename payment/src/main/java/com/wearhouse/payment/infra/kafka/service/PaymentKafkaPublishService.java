package com.wearhouse.payment.infra.kafka.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.payment.domain.payment.event.PaymentDomainEvent;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class PaymentKafkaPublishService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final long sendTimeoutMs;

    public PaymentKafkaPublishService(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PaymentKafkaFlowMetrics paymentKafkaFlowMetrics,
            @Value("${wearhouse.payment.kafka.send-timeout-ms:3000}") long sendTimeoutMs
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.paymentKafkaFlowMetrics = paymentKafkaFlowMetrics;
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public void send(PaymentDomainEvent event) {
        String payload = serialize(event);
        String key = event.getPartitionKey() == null || event.getPartitionKey().isBlank()
                ? event.getEventId()
                : event.getPartitionKey();
        paymentKafkaFlowMetrics.incrementPublishAttempt(event.getEventType(), event.getTopic());
        try {
            kafkaTemplate.send(event.getTopic(), key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
            paymentKafkaFlowMetrics.incrementPublishSuccess(event.getEventType(), event.getTopic());
        } catch (Exception exception) {
            paymentKafkaFlowMetrics.incrementPublishFailure(event.getEventType(), event.getTopic());
            throw new IllegalStateException("Payment 이벤트 Kafka 발행에 실패했습니다.", exception);
        }
    }

    private String serialize(PaymentDomainEvent event) {
        try {
            return objectMapper.writeValueAsString(event.toEnvelope());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Payment 이벤트 직렬화에 실패했습니다.", exception);
        }
    }
}
