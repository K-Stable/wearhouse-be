package com.wearhouse.common.infra.feign.userauth.dto;

public record UserAuthSignupRequest(
        String userType,
        String email,
        String passwordHash,
        String displayName
) {
}
