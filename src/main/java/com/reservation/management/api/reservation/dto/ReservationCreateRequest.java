package com.reservation.management.api.reservation.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ReservationCreateRequest(
        String customerName,
        String reservedAt,
        @NotNull
        @Min(1)
        Integer partySize
) {
}
