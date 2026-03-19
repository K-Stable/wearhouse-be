package com.wearhouse.payment.domain.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record StablepaySessionPrepareRequest(
        @NotNull Long orderId,
        @NotBlank String orderNo,
        @NotNull @DecimalMin("0.01") BigDecimal amount,
        @NotBlank String payerAddress,
        @NotBlank String tokenAddress,
        @NotBlank String chainId
) {
}
