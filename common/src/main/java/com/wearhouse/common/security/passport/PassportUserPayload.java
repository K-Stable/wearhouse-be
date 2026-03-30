package com.wearhouse.common.security.passport;

import java.util.List;

public record PassportUserPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
