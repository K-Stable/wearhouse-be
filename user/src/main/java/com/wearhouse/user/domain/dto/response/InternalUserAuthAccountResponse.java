package com.wearhouse.user.domain.dto.response;

public record InternalUserAuthAccountResponse(
        Long userId,
        String userType,
        String email,
        String passwordHash,
        String status,
        Long userVersion
) {
}
