package com.wearhouse.payment.kafka.publisher;

import com.wearhouse.common.support.config.OutboxProperties;
import com.wearhouse.payment.infra.jpa.repository.PaymentOutboxRepository;
import com.wearhouse.payment.support.monitoring.PaymentKafkaFlowMetrics;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final PaymentOutboxRepository paymentOutboxRepository;
    private final PaymentKafkaFlowMetrics paymentKafkaFlowMetrics;
    private final OutboxProperties outboxProperties;

    public void send(
            String eventId,
            String eventType,
            String topic,
            String partitionKey,
            String payload,
            String trigger
    ) {
        String key = partitionKey == null || partitionKey.isBlank() ? eventId : partitionKey;
        paymentKafkaFlowMetrics.incrementPublishAttempt(eventType, topic, trigger);
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
            paymentOutboxRepository.markSuccess(eventId);
            paymentKafkaFlowMetrics.incrementPublishSuccess(eventType, topic, trigger);
        } catch (Exception exception) {
            paymentOutboxRepository.markFail(
                    eventId,
                    "KAFKA_SEND_ERROR",
                    exception.getMessage()
            );
            paymentKafkaFlowMetrics.incrementPublishFailure(eventType, topic, trigger, "fail");
        }
    }
}
