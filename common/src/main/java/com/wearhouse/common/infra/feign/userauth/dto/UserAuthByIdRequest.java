package com.wearhouse.common.infra.feign.userauth.dto;

public record UserAuthByIdRequest(
        String userType,
        Long userId
) {
}
