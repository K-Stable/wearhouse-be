package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InternalUserAuthByIdRequest(
        @NotBlank String userType,
        @NotNull Long userId
) {
}
