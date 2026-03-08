package com.wearhouse.order.support.security;

import java.util.List;

public record OrderPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
