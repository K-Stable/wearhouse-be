package com.wearhouse.common.global.error;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.global.response.ValidationError;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(basePackages = "com.wearhouse")
public class GlobalExceptionHandler {

    @ExceptionHandler(ErrorException.class)
    public ResponseEntity<ApiResponse<Void>> handleErrorException(ErrorException exception) {
        ErrorCode errorCode = exception.errorCode();
        ApiResponse<Void> response = ApiResponse.failure(
                errorCode.code(),
                exception.getMessage() == null ? errorCode.message() : exception.getMessage(),
                null
        );
        return ResponseEntity.status(errorCode.status()).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception
    ) {
        List<ValidationError> validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ValidationError::from)
                .collect(Collectors.toList());

        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.status())
                .body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT_VALUE, validationErrors));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiResponse<List<ValidationError>>> handleBindException(BindException exception) {
        List<ValidationError> validationErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(ValidationError::from)
                .collect(Collectors.toList());

        return ResponseEntity.status(CommonErrorCode.INVALID_INPUT_VALUE.status())
                .body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT_VALUE, validationErrors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException exception
    ) {
        Map<String, String> violationMap = exception.getConstraintViolations()
                .stream()
                .collect(Collectors.toMap(
                        violation -> violation.getPropertyPath().toString(),
                        violation -> violation.getMessage(),
                        (left, right) -> left
                ));

        return ResponseEntity.status(CommonErrorCode.CONSTRAINT_VIOLATION.status())
                .body(ApiResponse.failure(CommonErrorCode.CONSTRAINT_VIOLATION, violationMap));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException exception
    ) {
        Map<String, String> details = Map.of(
                "parameterName", exception.getParameterName(),
                "parameterType", exception.getParameterType()
        );

        return ResponseEntity.status(CommonErrorCode.MISSING_REQUEST_PARAMETER.status())
                .body(ApiResponse.failure(CommonErrorCode.MISSING_REQUEST_PARAMETER, details));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException exception
    ) {
        Map<String, String> details = Map.of(
                "parameterName", exception.getName(),
                "requiredType", exception.getRequiredType() == null ? "unknown" : exception.getRequiredType().getSimpleName()
        );

        return ResponseEntity.status(CommonErrorCode.TYPE_MISMATCH.status())
                .body(ApiResponse.failure(CommonErrorCode.TYPE_MISMATCH, details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception
    ) {
        return ResponseEntity.status(CommonErrorCode.MESSAGE_NOT_READABLE.status())
                .body(ApiResponse.failure(CommonErrorCode.MESSAGE_NOT_READABLE));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException exception
    ) {
        Map<String, String> details = Map.of(
                "method", exception.getMethod(),
                "supportedMethods", exception.getSupportedHttpMethods() == null
                        ? "[]"
                        : exception.getSupportedHttpMethods().toString()
        );

        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.status())
                .body(ApiResponse.failure(CommonErrorCode.METHOD_NOT_ALLOWED, details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception exception) {
        return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.status())
                .body(ApiResponse.failure(CommonErrorCode.INTERNAL_SERVER_ERROR));
    }
}
