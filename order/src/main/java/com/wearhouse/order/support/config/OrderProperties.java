package com.wearhouse.order.support.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.order")
public record OrderProperties(
        @Positive
        @DefaultValue("3000")
        long paymentConfirmWaitTimeoutMs,

        @Positive
        @DefaultValue("100")
        long paymentConfirmWaitIntervalMs,

        @Positive
        @DefaultValue("3000")
        long paymentPrepareWaitTimeoutMs,

        @Positive
        @DefaultValue("100")
        long paymentPrepareWaitIntervalMs,

        @Positive
        @DefaultValue("7")
        long deliveryPurchaseConfirmDelayDays,

        @Positive
        @DefaultValue("60000")
        long deliveryPurchaseConfirmSchedulerIntervalMs,

        @NotBlank
        @DefaultValue("https://wear-house.shop/payments/stable/success")
        String paymentPrepareSuccessUrlTemplate,

        @NotBlank
        @DefaultValue("https://wear-house.shop/payments/stable/fail")
        String paymentPrepareFailUrlTemplate
) {
}
