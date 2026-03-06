package com.wearhouse.order.infra.kafka.service;

import com.wearhouse.order.infra.kafka.dto.KafkaTestPublishResponse;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class KafkaTestMessageService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String testTopic;

    public KafkaTestMessageService(
            KafkaTemplate<String, String> kafkaTemplate,
            @Value("${wearhouse.kafka.test-topic}") String testTopic
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.testTopic = testTopic;
    }

    public KafkaTestPublishResponse publishTestMessage(String message) {
        String key = UUID.randomUUID().toString();
        String payload = (message == null || message.isBlank())
                ? "wearhouse kafka test message"
                : message;

        kafkaTemplate.send(testTopic, key, payload);

        return KafkaTestPublishResponse.builder()
                .topic(testTopic)
                .key(key)
                .message(payload)
                .publishedAt(LocalDateTime.now().toString())
                .build();
    }
}
