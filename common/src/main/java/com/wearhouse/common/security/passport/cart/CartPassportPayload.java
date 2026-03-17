package com.wearhouse.common.security.passport.cart;

import java.util.List;

public record CartPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
