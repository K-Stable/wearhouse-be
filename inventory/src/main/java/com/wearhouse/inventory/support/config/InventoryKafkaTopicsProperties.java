package com.wearhouse.inventory.support.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "wearhouse.kafka")
public class InventoryKafkaTopicsProperties {

    @NotBlank
    private String inventoryCommandTopic = "wearhouse.inventory.command.v1";

    @NotBlank
    private String inventoryEventTopic = "wearhouse.inventory.event.v1";

    @NotBlank
    private String orderEventTopic = "wearhouse.order.event.v1";
}
