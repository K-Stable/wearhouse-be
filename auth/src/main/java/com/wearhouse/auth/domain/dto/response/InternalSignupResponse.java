package com.wearhouse.auth.domain.dto.response;

import java.time.LocalDateTime;

public record InternalSignupResponse(
        Long userId,
        String userType,
        String email,
        LocalDateTime accessTokenExpiresAt,
        String accessToken,
        String refreshToken
) {
}
