package com.wearhouse.user.domain.dto.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.wearhouse.user.domain.validation.UserValidationPattern;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record BuyerSignupRequest(

        @NotBlank
        @Pattern(regexp = UserValidationPattern.LOGIN_ID_REGEX)
        @JsonAlias("logingId")
        String loginId,

        @NotBlank
        @Pattern(regexp = UserValidationPattern.PASSWORD_REGEX)
        String password,

        @NotBlank
        String passwordConfirm,

        @NotBlank
        String name,

        @Email @NotBlank
        String email,

        @NotBlank
        String phone
) {
}
