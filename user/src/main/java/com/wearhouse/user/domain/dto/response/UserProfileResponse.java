package com.wearhouse.user.domain.dto.response;

public record UserProfileResponse(
        Long userId,
        String userType,
        String email,
        String name,
        String status,
        Long userVersion
) {
}
