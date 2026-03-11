package com.wearhouse.user.domain.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import com.wearhouse.user.domain.validation.UserValidationPattern;

public record PasswordChangeRequest(
        @NotBlank String currentPassword,
        @NotBlank
        @Pattern(regexp = UserValidationPattern.PASSWORD_REGEX)
        String newPassword,
        @NotBlank String newPasswordConfirm
) {
}
