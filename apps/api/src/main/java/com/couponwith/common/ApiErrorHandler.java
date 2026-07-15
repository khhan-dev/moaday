package com.couponwith.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiErrorHandler {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiError> handleApi(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status()).body(new ApiError(
                exception.code(), exception.getMessage(), request.getRequestURI(), Instant.now(), Map.of()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        var details = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        error -> error.getField(),
                        DefaultMessageSourceResolvable::getDefaultMessage,
                        (first, ignored) -> first));
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", "입력값을 확인해 주세요.", request.getRequestURI(), Instant.now(), details));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiError> handleConstraint(ConstraintViolationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_ERROR", exception.getMessage(), request.getRequestURI(), Instant.now(), Map.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleMalformedJson(HttpMessageNotReadableException exception,
                                                  HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                "MALFORMED_JSON", "요청 본문의 JSON 형식을 확인해 주세요.",
                request.getRequestURI(), Instant.now(), Map.of()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                "INTERNAL_ERROR", "요청을 처리하지 못했습니다.", request.getRequestURI(), Instant.now(), Map.of()));
    }

    record ApiError(String code, String message, String path, Instant timestamp, Map<String, ?> details) {}
}
