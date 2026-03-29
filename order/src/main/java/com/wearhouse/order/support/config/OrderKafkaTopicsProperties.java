package com.wearhouse.order.support.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.kafka")
public record OrderKafkaTopicsProperties(
        @NotBlank
        @DefaultValue("wearhouse.inventory.command.v1")
        String inventoryReserveTopic,

        @NotBlank
        @DefaultValue("wearhouse.inventory.command.v1")
        String inventoryCommandTopic,

        @NotBlank
        @DefaultValue("wearhouse.inventory.event.v1")
        String inventoryEventTopic,

        @NotBlank
        @DefaultValue("wearhouse.payment.command.v1")
        String paymentPrepareTopic,

        @NotBlank
        @DefaultValue("wearhouse.payment.event.v1")
        String paymentEventTopic,

        @NotBlank
        @DefaultValue("wearhouse.order.event.v1")
        String orderEventTopic
) {
}
