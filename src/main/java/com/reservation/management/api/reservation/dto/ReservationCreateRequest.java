package com.reservation.management.api.reservation.dto;

public record ReservationCreateRequest(
        String customerName,
        String reservedAt
) {
}
