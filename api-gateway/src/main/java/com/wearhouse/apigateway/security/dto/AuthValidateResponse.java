package com.wearhouse.apigateway.security.dto;

import java.time.LocalDateTime;
import java.util.List;

public record AuthValidateResponse(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion,
        LocalDateTime tokenIssuedAt,
        LocalDateTime tokenExpiresAt
) {
}
