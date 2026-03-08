package com.wearhouse.auth.infra.feign.dto;

public record UserAuthAccountResponse(
        Long userId,
        String userType,
        String email,
        String passwordHash,
        String status,
        Long userVersion
) {
}
