package com.wearhouse.common.security.passport.payment;

import java.util.List;

public record PaymentPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
