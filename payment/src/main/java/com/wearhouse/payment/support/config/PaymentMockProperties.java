package com.wearhouse.payment.support.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.payment.mock")
public record PaymentMockProperties(
        @Positive
        @DefaultValue("30")
        int pendingTimeoutMinutes,

        @Positive
        @DefaultValue("10000")
        long timeoutCheckIntervalMs,

        @Positive
        @DefaultValue("200")
        int timeoutBatchSize,

        @NotBlank
        @DefaultValue("FAIL")
        String failMethods,

        @NotBlank
        @DefaultValue("TIMEOUT")
        String timeoutMethods
) {
}
