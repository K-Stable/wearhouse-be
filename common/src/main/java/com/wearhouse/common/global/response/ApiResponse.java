package com.wearhouse.common.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wearhouse.common.global.error.ErrorCode;
import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        LocalDateTime timestamp
) {

    public static <T> ApiResponse<T> success(T data) {
        return success(CommonSuccessCode.SUCCESS, data);
    }

    public static ApiResponse<Void> success(SuccessCode successCode) {
        return new ApiResponse<>(true, successCode.code(), successCode.message(), null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(SuccessCode successCode, T data) {
        return new ApiResponse<>(true, successCode.code(), successCode.message(), data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> success(String code, String message, T data) {
        return new ApiResponse<>(true, code, message, data, LocalDateTime.now());
    }

    public static ApiResponse<Void> failure(ErrorCode errorCode) {
        return new ApiResponse<>(false, errorCode.code(), errorCode.message(), null, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> failure(ErrorCode errorCode, T data) {
        return new ApiResponse<>(false, errorCode.code(), errorCode.message(), data, LocalDateTime.now());
    }

    public static <T> ApiResponse<T> failure(String code, String message, T data) {
        return new ApiResponse<>(false, code, message, data, LocalDateTime.now());
    }
}
