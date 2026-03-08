package com.wearhouse.user.support.security;

import java.util.List;

public record UserPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
