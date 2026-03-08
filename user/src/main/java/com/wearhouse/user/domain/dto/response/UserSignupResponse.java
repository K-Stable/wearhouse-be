package com.wearhouse.user.domain.dto.response;

import java.time.LocalDateTime;

public record UserSignupResponse(
        Long userId,
        String userType,
        String email,
        LocalDateTime accessTokenExpiresAt
) {
}
