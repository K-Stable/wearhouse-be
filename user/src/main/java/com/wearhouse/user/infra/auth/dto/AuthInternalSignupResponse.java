package com.wearhouse.user.infra.auth.dto;

import java.time.LocalDateTime;

public record AuthInternalSignupResponse(
        Long userId,
        String userType,
        String email,
        LocalDateTime accessTokenExpiresAt,
        String accessToken,
        String refreshToken
) {
}
