package com.wearhouse.order.buyer.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OrderPaymentConfirmRequest(
        @NotBlank
        @JsonAlias("orderId")
        String orderNo,
        @NotBlank String paymentKey,
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
