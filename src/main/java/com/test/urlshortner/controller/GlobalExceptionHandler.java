package com.test.urlshortner.controller;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("invalid format for longURL");

        log.warn("Validation failed for request: {}", message, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message.contains("longURL") ? "invalid format for longURL" : message));
    }

    @ExceptionHandler({ConstraintViolationException.class, IllegalArgumentException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("longURL")) {
            message = "invalid format for longURL";
        }
        if (message == null || message.isBlank()) {
            message = "invalid format for longURL";
        }

        log.warn("Bad request received: {}", message, ex);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), message));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(IllegalStateException ex) {
        String message = ex.getMessage() == null ? "couldn't find a matching long URL to redirect" : ex.getMessage();
        if (message.contains("Short URL not found") || message.contains("matching long URL")) {
            message = "couldn't find a matching long URL to redirect";
        }

        log.warn("Resource not found: {}", message, ex);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleServerException(Exception ex) {
        log.error("Unhandled server exception: {}", ex.getClass().getSimpleName(), ex);
        // Don't expose raw exception details to clients
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.value(), "Internal server error"));
    }
}
