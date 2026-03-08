package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record InternalUserAuthByEmailRequest(
        @NotBlank String userType,
        @Email @NotBlank String email
) {
}
