package com.wearhouse.order.global.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@EnableKafka
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic testTopic(@Value("${wearhouse.kafka.test-topic}") String testTopic) {
        return TopicBuilder.name(testTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
