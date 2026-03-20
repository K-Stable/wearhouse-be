package com.wearhouse.order.infra.kafka.producer;

import com.wearhouse.common.support.config.OutboxProperties;
import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxProperties outboxProperties;

    public void send(
            String topic,
            String partitionKey,
            String payload
    ) {
        String key = partitionKey == null || partitionKey.isBlank() ? null : partitionKey;
        try {
            kafkaTemplate.send(topic, key, payload).get(outboxProperties.sendTimeoutMs(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("주문 Kafka 메시지 전송에 실패했습니다.", exception);
        }
    }
}
