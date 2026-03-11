package com.wearhouse.common.security.passport.order;

import java.util.List;

public record OrderPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
