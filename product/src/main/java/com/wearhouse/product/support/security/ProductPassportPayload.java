package com.wearhouse.product.support.security;

import java.util.List;

public record ProductPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
