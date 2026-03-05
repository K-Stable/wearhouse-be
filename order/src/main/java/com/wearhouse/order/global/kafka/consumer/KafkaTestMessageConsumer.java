package com.wearhouse.order.global.kafka.consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
public class KafkaTestMessageConsumer {

    private static final Logger log = LoggerFactory.getLogger(KafkaTestMessageConsumer.class);

    @KafkaListener(topics = "${wearhouse.kafka.test-topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consumeTestMessage(
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Payload String message
    ) {
        log.info("Received kafka test message. key={}, message={}", key, message);
    }
}
