package com.wearhouse.payment.support.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.kafka")
public record PaymentKafkaTopicsProperties(
        @NotBlank
        @DefaultValue("wearhouse.payment.command.v1")
        String paymentPrepareTopic,

        @NotBlank
        @DefaultValue("wearhouse.payment.event.v1")
        String paymentEventTopic
) {
}
