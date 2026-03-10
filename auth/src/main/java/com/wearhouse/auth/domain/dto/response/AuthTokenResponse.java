package com.wearhouse.auth.domain.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthTokenResponse(
        Long userId,
        String userType,
        String email,
        LocalDateTime accessTokenExpiresAt,
        String accessToken,
        String refreshToken
) {
}
