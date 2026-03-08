package com.wearhouse.auth.infra.feign.dto;

public record UserAuthSignupRequest(
        String userType,
        String email,
        String passwordHash,
        String displayName
) {
}
