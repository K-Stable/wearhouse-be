package com.wearhouse.user.domain.validation;

public final class UserValidationPattern {

    private UserValidationPattern() {
    }

    public static final String PASSWORD_REGEX =
            "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+=\\-\\[\\]{};':\"\\\\|,.<>/?])[A-Za-z\\d!@#$%^&*()_+=\\-\\[\\]{};':\"\\\\|,.<>/?]{8,20}$";

    public static final String LOGIN_ID_REGEX = "^[a-z0-9._-]{4,16}$";
}
