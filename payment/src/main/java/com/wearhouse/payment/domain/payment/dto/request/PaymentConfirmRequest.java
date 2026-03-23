package com.wearhouse.payment.domain.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record PaymentConfirmRequest(
        @NotNull Long orderId,
        @NotBlank String orderNo,
        @NotBlank String paymentKey,
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
