package com.wearhouse.user.infra.auth.dto;

public record AuthInternalSignupRequest(
        String email,
        String password,
        String displayName
) {
}
