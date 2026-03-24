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

    @Positive
    private long paymentPrepareWaitTimeoutMs = 3000L;

    @Positive
    private long paymentPrepareWaitIntervalMs = 100L;

    @NotBlank
    private String paymentPrepareSuccessUrlTemplate = "https://mall.wearhouse.com/orders/{orderNo}/payments/success";

    @NotBlank
    private String paymentPrepareFailUrlTemplate = "https://mall.wearhouse.com/orders/{orderNo}/payments/fail";

    @Valid
    private Internal internal = new Internal();

    @Getter
    @Setter
    public static class Internal {
        @NotBlank
        private String sharedSecret = "wearhouse-order-internal-secret";
    }
}
