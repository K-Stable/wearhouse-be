package com.wearhouse.order.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class OrderRuntimePropertiesValidator {

    private final OrderKafkaTopicsProperties kafkaTopicsProperties;

    public OrderRuntimePropertiesValidator(OrderKafkaTopicsProperties kafkaTopicsProperties) {
        this.kafkaTopicsProperties = kafkaTopicsProperties;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.inventory-reserve-topic", kafkaTopicsProperties.getInventoryReserveTopic());
        requireText("wearhouse.kafka.inventory-command-topic", kafkaTopicsProperties.getInventoryCommandTopic());
        requireText("wearhouse.kafka.inventory-event-topic", kafkaTopicsProperties.getInventoryEventTopic());
        requireText("wearhouse.kafka.payment-prepare-topic", kafkaTopicsProperties.getPaymentPrepareTopic());
        requireText("wearhouse.kafka.payment-event-topic", kafkaTopicsProperties.getPaymentEventTopic());
        requireText("wearhouse.kafka.order-event-topic", kafkaTopicsProperties.getOrderEventTopic());
    }

    private void requireText(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 값은 비어 있을 수 없습니다.");
        }
    }

}
