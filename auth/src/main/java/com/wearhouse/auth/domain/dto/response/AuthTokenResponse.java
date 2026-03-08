package com.wearhouse.auth.domain.dto.response;

import java.time.LocalDateTime;

public record AuthTokenResponse(
        Long userId,
        String userType,
        String email,
        LocalDateTime accessTokenExpiresAt
) {
}
