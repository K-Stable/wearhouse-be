package com.wearhouse.order.support.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderRuntimePropertiesValidator {

    private final String inventoryReserveTopic;
    private final String inventoryCommandTopic;
    private final String inventoryEventTopic;
    private final String paymentPrepareTopic;
    private final String paymentEventTopic;
    private final String orderEventTopic;

    public OrderRuntimePropertiesValidator(
            @Value("${wearhouse.kafka.inventory-reserve-topic:}") String inventoryReserveTopic,
            @Value("${wearhouse.kafka.inventory-command-topic:}") String inventoryCommandTopic,
            @Value("${wearhouse.kafka.inventory-event-topic:}") String inventoryEventTopic,
            @Value("${wearhouse.kafka.payment-prepare-topic:}") String paymentPrepareTopic,
            @Value("${wearhouse.kafka.payment-event-topic:}") String paymentEventTopic,
            @Value("${wearhouse.kafka.order-event-topic:}") String orderEventTopic
    ) {
        this.inventoryReserveTopic = inventoryReserveTopic;
        this.inventoryCommandTopic = inventoryCommandTopic;
        this.inventoryEventTopic = inventoryEventTopic;
        this.paymentPrepareTopic = paymentPrepareTopic;
        this.paymentEventTopic = paymentEventTopic;
        this.orderEventTopic = orderEventTopic;
    }

    @PostConstruct
    void validate() {
        requireText("wearhouse.kafka.inventory-reserve-topic", inventoryReserveTopic);
        requireText("wearhouse.kafka.inventory-command-topic", inventoryCommandTopic);
        requireText("wearhouse.kafka.inventory-event-topic", inventoryEventTopic);
        requireText("wearhouse.kafka.payment-prepare-topic", paymentPrepareTopic);
        requireText("wearhouse.kafka.payment-event-topic", paymentEventTopic);
        requireText("wearhouse.kafka.order-event-topic", orderEventTopic);
    }

    private void requireText(String key, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " 값은 비어 있을 수 없습니다.");
        }
    }

}
