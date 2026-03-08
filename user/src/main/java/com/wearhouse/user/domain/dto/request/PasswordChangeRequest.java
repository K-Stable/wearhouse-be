package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank String newPassword
) {
}
