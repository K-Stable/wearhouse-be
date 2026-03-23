package com.wearhouse.order.domain.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record OrderPaymentConfirmRequest(
        @NotNull Long orderId,
        String paymentKey,
        @NotNull @DecimalMin("0.01") BigDecimal amount
) {
}
