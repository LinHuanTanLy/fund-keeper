package com.fundkeeper.backend.shared.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.fundkeeper.backend.shared.api.ApiResponse;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException exception) {
        var errorCode = exception.errorCode();
        return ResponseEntity
                .status(errorCode.httpStatus())
                .body(ApiResponse.error(errorCode.name(), exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiResponse<Map<String, String>>> handleValidation(
            MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        var body = new ApiResponse<>(
                ErrorCode.INVALID_REQUEST.name(),
                "请求参数不合法",
                fields,
                java.time.Instant.now());
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> handleMalformedRequest(Exception exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(
                        ErrorCode.INVALID_REQUEST.name(),
                        "请求格式不正确"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error(
                "Unhandled server error on {} ({})",
                request.getRequestURI(),
                exception.getClass().getName());
        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(
                        ErrorCode.INTERNAL_ERROR.name(),
                        "服务暂时不可用"));
    }
}
