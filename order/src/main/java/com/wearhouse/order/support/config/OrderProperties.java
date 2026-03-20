package com.wearhouse.order.support.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "wearhouse.order")
public class OrderProperties {

    @Positive
    private long paymentConfirmWaitTimeoutMs = 3000L;

    @Positive
    private long paymentConfirmWaitIntervalMs = 100L;

    @Valid
    private Internal internal = new Internal();

    @Getter
    @Setter
    public static class Internal {
        @NotBlank
        private String sharedSecret = "wearhouse-order-internal-secret";
    }
}
