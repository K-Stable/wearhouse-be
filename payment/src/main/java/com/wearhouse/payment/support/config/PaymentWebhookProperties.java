package com.wearhouse.payment.support.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.pay.webhook")
public record PaymentWebhookProperties(
        @NotBlank
        @DefaultValue("pay-webhook-secret")
        String secret,

        @PositiveOrZero
        @DefaultValue("300")
        long allowedSkewSeconds
) {
}
