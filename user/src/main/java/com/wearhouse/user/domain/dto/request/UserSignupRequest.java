package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UserSignupRequest(
        @Email @NotBlank String email,
        @NotBlank String password,
        @NotBlank String displayName
) {
}
