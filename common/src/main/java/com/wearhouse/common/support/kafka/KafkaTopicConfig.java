package com.wearhouse.common.support.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@EnableKafka
@Configuration
@ConditionalOnProperty(name = "wearhouse.kafka.common-config-enabled", havingValue = "true")
public class KafkaTopicConfig {

    @Bean
    public NewTopic testTopic(@Value("${wearhouse.kafka.test-topic:wearhouse.test.topic}") String testTopic) {
        return TopicBuilder.name(testTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryCommandTopic(
            @Value("${wearhouse.kafka.inventory-command-topic:wearhouse.inventory.command.v1}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic inventoryEventTopic(
            @Value("${wearhouse.kafka.inventory-event-topic:wearhouse.inventory.event.v1}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentCommandTopic(
            @Value("${wearhouse.kafka.payment-prepare-topic:wearhouse.payment.command.v1}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic paymentEventTopic(
            @Value("${wearhouse.kafka.payment-event-topic:wearhouse.payment.event.v1}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderEventTopic(
            @Value("${wearhouse.kafka.order-event-topic:wearhouse.order.event.v1}") String topicName
    ) {
        return TopicBuilder.name(topicName)
                .partitions(6)
                .replicas(1)
                .build();
    }
}
