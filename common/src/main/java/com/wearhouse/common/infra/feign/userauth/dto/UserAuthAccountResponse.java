package com.wearhouse.common.infra.feign.userauth.dto;

public record UserAuthAccountResponse(
        Long userId,
        String userType,
        String email,
        String passwordHash,
        String status,
        Long userVersion
) {
}
