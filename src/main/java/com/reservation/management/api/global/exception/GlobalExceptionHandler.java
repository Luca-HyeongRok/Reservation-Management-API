package com.reservation.management.api.global.exception;

import java.time.Instant;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage(), "Invalid request.");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NoSuchElementException e) {
        return errorResponse(HttpStatus.NOT_FOUND, e.getMessage(), "Resource not found.");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleConflict(IllegalStateException e) {
        return errorResponse(HttpStatus.CONFLICT, e.getMessage(), "Request conflicts with current state.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleInternalServerError(Exception e) {
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage(), "Internal server error.");
    }

    private ResponseEntity<ErrorResponse> errorResponse(HttpStatus status, String message, String fallbackMessage) {
        String safeMessage = (message == null || message.isBlank()) ? fallbackMessage : message;

        ErrorResponse body = new ErrorResponse(
                safeMessage,
                Instant.now().toString(),
                status.value()
        );

        return ResponseEntity
                .status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }
}