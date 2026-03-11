package com.wearhouse.common.security.passport.product;

import java.util.List;

public record ProductPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
