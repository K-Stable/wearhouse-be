package com.wearhouse.order.kafka.publisher;

import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderKafkaProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    public CompletableFuture<SendResult<String, String>> send(
            String topic,
            String partitionKey,
            String payload
    ) {
        String key = partitionKey == null || partitionKey.isBlank() ? null : partitionKey;
        try {
            return kafkaTemplate.send(topic, key, payload);
        } catch (Exception exception) {
            throw new IllegalStateException("주문 Kafka 메시지 전송에 실패했습니다.", exception);
        }
    }
}
