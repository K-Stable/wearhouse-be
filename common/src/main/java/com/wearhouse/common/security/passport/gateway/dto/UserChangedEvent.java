package com.wearhouse.common.security.passport.gateway.dto;

public record UserChangedEvent(
        String userType,
        Long userId,
        String reason
) {
}
