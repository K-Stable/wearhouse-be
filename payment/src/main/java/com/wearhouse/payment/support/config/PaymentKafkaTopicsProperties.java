package com.wearhouse.payment.support.config;

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
public class PaymentKafkaTopicsProperties {

    @NotBlank
    private String paymentPrepareTopic = "wearhouse.payment.command.v1";

    @NotBlank
    private String paymentEventTopic = "wearhouse.payment.event.v1";
}
