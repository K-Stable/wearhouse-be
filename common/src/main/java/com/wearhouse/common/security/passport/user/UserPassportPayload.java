package com.wearhouse.common.security.passport.user;

import java.util.List;

public record UserPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
