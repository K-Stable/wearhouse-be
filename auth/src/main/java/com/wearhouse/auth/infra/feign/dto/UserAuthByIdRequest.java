package com.wearhouse.auth.infra.feign.dto;

public record UserAuthByIdRequest(
        String userType,
        Long userId
) {
}
