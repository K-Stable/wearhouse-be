package com.wearhouse.auth.infra.feign.dto;

public record UserAuthByLoginIdRequest(
        String userType,
        String loginId
) {
}
