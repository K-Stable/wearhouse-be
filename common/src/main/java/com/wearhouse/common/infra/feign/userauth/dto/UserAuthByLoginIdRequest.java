package com.wearhouse.common.infra.feign.userauth.dto;

public record UserAuthByLoginIdRequest(
        String userType,
        String loginId
) {
}
