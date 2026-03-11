package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InternalUserAuthByLoginIdRequest(
        @NotBlank String userType,
        @NotBlank String loginId
) {
}
