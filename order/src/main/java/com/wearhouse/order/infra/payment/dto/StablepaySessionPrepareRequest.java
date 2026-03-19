package com.wearhouse.order.infra.payment.dto;

import java.math.BigDecimal;

public record StablepaySessionPrepareRequest(
        Long orderId,
        String orderNo,
        BigDecimal amount,
        String payerAddress,
        String tokenAddress,
        String chainId
) {
}
