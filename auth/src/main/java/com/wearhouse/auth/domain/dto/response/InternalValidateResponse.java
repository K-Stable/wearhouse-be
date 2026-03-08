package com.wearhouse.auth.domain.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public record InternalValidateResponse(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion,
        LocalDateTime tokenIssuedAt,
        LocalDateTime tokenExpiresAt
) {
}
