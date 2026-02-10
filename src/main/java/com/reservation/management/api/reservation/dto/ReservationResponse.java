package com.reservation.management.api.reservation.dto;

public record ReservationResponse(
        Long reservationId,
        String customerName,
        String reservedAt,
        String status
) {
}
