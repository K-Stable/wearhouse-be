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
        return new ApiResponse<>(true, "SUCCESS", "요청이 성공적으로 처리되었습니다.", data, LocalDateTime.now());
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
