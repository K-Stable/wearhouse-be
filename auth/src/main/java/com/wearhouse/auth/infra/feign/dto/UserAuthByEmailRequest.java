package com.wearhouse.auth.infra.feign.dto;

public record UserAuthByEmailRequest(
        String userType,
        String email
) {
}
