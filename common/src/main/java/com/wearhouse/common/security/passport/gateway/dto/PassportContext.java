package com.wearhouse.common.security.passport.gateway.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PassportContext(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion,
        LocalDateTime tokenIssuedAt,
        LocalDateTime tokenExpiresAt
) {
}
