package com.wearhouse.common.global.response;

import org.springframework.validation.FieldError;

public record ValidationError(
        String field,
        String message,
        Object rejectedValue
) {

    public static ValidationError from(FieldError fieldError) {
        return new ValidationError(
                fieldError.getField(),
                fieldError.getDefaultMessage(),
                fieldError.getRejectedValue()
        );
    }
}
