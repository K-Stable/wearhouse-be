package com.wearhouse.apigateway.security.dto;

public record UserChangedEvent(
        String userType,
        Long userId,
        String reason
) {
}
