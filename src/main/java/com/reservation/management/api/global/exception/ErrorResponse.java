package com.reservation.management.api.global.exception;

public record ErrorResponse(
        String message,
        String timestamp,
        int status
) {
}