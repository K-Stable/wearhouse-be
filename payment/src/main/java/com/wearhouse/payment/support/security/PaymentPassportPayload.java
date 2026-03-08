package com.wearhouse.payment.support.security;

import java.util.List;

public record PaymentPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
