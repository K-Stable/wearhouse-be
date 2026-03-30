package com.wearhouse.payment.support.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "wearhouse.pay")
public record PaymentPayProperties(
        @NotBlank
        @DefaultValue("https://api.kst-wallet.xyz")
        String apiBaseUrl,

        @NotBlank
        @DefaultValue("pay_client_key")
        String clientKey,

        @NotBlank
        @DefaultValue("pay_secret_key")
        String secretKey,

        @Positive
        @DefaultValue("10000")
        long timeoutMs,

        @NotBlank
        @DefaultValue("/api/v1/merchant/checkout-sessions")
        String preparePath,

        @NotBlank
        @DefaultValue("/api/v1/merchant/payments/confirm")
        String confirmPath
) {
}
