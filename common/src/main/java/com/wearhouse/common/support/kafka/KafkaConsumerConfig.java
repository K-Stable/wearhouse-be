package com.wearhouse.common.support.kafka;

import com.wearhouse.common.global.error.ErrorException;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
@EnableConfigurationProperties(KafkaErrorHandlerProperties.class)
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
    public DefaultErrorHandler kafkaDefaultErrorHandler(
            KafkaTemplate<String, String> kafkaTemplate,
            KafkaErrorHandlerProperties properties
    ) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (consumerRecord, exception) ->
                        new TopicPartition(properties.getDltTopic(), -1)
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff(properties));
        errorHandler.addNotRetryableExceptions(ErrorException.class);
        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
        return errorHandler;
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
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        Integer concurrency = kafkaProperties.getListener().getConcurrency();
        if (concurrency != null && concurrency > 0) {
            factory.setConcurrency(concurrency);
        }
        return factory;
    }

    private BackOff backOff(KafkaErrorHandlerProperties properties) {
        long retryAttempts = Math.max(0L, properties.getRetryAttempts());
        long retryIntervalMs = Math.max(1L, properties.getRetryIntervalMs());
        return new FixedBackOff(retryIntervalMs, retryAttempts);
    }
}
