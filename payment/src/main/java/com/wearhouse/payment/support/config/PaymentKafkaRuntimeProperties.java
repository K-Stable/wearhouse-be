package com.wearhouse.payment.support.config;

import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.payment.kafka")
public record PaymentKafkaRuntimeProperties(
        @Positive
        @DefaultValue("3000")
        long sendTimeoutMs
) {
}
