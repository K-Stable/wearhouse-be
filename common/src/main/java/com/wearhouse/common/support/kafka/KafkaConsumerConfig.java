package com.wearhouse.common.support.kafka;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@ConditionalOnProperty(name = "wearhouse.kafka.common-config-enabled", havingValue = "true")
public class KafkaConsumerConfig {

    @Bean(name = "kafkaConsumerFactory")
    @ConditionalOnMissingBean(name = "kafkaConsumerFactory")
    public ConsumerFactory<String, String> kafkaConsumerFactory(KafkaProperties kafkaProperties) {
        Map<String, Object> consumerProperties = new HashMap<>(kafkaProperties.buildConsumerProperties());
        consumerProperties.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProperties.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new DefaultKafkaConsumerFactory<>(consumerProperties);
    }

    @Bean(name = "kafkaDefaultErrorHandler")
    @ConditionalOnMissingBean(name = "kafkaDefaultErrorHandler")
    public DefaultErrorHandler kafkaDefaultErrorHandler() {
        return new DefaultErrorHandler(new FixedBackOff(1000L, 2L));
    }

    @Bean(name = "kafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = "kafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> kafkaConsumerFactory,
            DefaultErrorHandler kafkaDefaultErrorHandler,
            KafkaProperties kafkaProperties
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(kafkaConsumerFactory);
        factory.setCommonErrorHandler(kafkaDefaultErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);

        Integer concurrency = kafkaProperties.getListener().getConcurrency();
        if (concurrency != null && concurrency > 0) {
            factory.setConcurrency(concurrency);
        }
        return factory;
    }
}
