package com.wearhouse.auth.domain.model;

public record AuthAccount(
        Long id,
        String email,
        String passwordHash,
        String displayName,
        String status,
        Long userVersion,
        AuthUserType userType
) {

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
