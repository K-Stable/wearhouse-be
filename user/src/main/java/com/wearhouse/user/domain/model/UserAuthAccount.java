package com.wearhouse.user.domain.model;

public record UserAuthAccount(
        Long userId,
        UserType userType,
        String email,
        String passwordHash,
        String status,
        Long userVersion
) {
}
