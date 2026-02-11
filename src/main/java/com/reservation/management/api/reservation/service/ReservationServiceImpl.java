package com.reservation.management.api.reservation.service;

import com.reservation.management.api.reservation.domain.Reservation;
import com.reservation.management.api.reservation.domain.ReservationStatus;
import com.reservation.management.api.reservation.dto.ReservationCreateRequest;
import com.reservation.management.api.reservation.dto.ReservationResponse;
import com.reservation.management.api.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ReservationServiceImpl implements ReservationService {

    private static final EnumSet<ReservationStatus> ACTIVE_STATUSES =
            EnumSet.of(ReservationStatus.REQUESTED, ReservationStatus.CONFIRMED);

    private static final EnumSet<ReservationStatus> CANNOT_CANCEL_STATUSES =
            EnumSet.of(ReservationStatus.CANCELED, ReservationStatus.COMPLETED, ReservationStatus.NO_SHOW);

    private static final String DEFAULT_CANCEL_REASON = "사용자 요청 취소";

    private final ReservationRepository reservationRepository;

    public ReservationServiceImpl(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Override
    public ReservationResponse createReservation(ReservationCreateRequest request) {
        validateCreateRequest(request);

        LocalDateTime reservedAt = parseReservedAt(request.reservedAt());

        if (!reservedAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("예약 시간은 현재 시각 이후여야 합니다.");
        }

        boolean duplicated = reservationRepository.existsByReservedAtAndStatusIn(reservedAt, ACTIVE_STATUSES);
        if (duplicated) {
            throw new IllegalStateException("동일 시간대에 이미 활성 예약이 존재합니다.");
        }

        Reservation reservation = new Reservation();
        reservation.setReservationNumber(generateReservationNumber());
        reservation.setCustomerName(request.customerName().trim());
        reservation.setCustomerPhone("UNKNOWN");
        reservation.setCustomerEmail(null);
        reservation.setReservedAt(reservedAt);
        reservation.setPartySize(request.partySize());
        reservation.setStatus(ReservationStatus.REQUESTED);
        reservation.setCancelReason(null);

        LocalDateTime now = LocalDateTime.now();
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);

        Reservation saved = reservationRepository.save(reservation);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ReservationResponse getReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("예약을 찾을 수 없습니다. id=" + reservationId));
        return toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations() {
        return reservationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ReservationResponse cancelReservation(Long reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("예약을 찾을 수 없습니다. id=" + reservationId));

        ReservationStatus currentStatus = reservation.getStatus();

        if (CANNOT_CANCEL_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("이미 종결된 예약은 취소할 수 없습니다. status=" + currentStatus);
        }

        if (!ACTIVE_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("현재 상태에서는 취소할 수 없습니다. status=" + currentStatus);
        }

        reservation.setStatus(ReservationStatus.CANCELED);
        reservation.setCancelReason(DEFAULT_CANCEL_REASON);
        reservation.setUpdatedAt(LocalDateTime.now());

        Reservation saved = reservationRepository.save(reservation);
        return toResponse(saved);
    }

    private void validateCreateRequest(ReservationCreateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("예약 요청은 필수입니다.");
        }

        if (request.customerName() == null || request.customerName().trim().isEmpty()) {
            throw new IllegalArgumentException("예약자 이름은 필수입니다.");
        }

        if (request.reservedAt() == null || request.reservedAt().trim().isEmpty()) {
            throw new IllegalArgumentException("예약 시간은 필수입니다.");
        }

        if (request.partySize() == null || request.partySize() < 1) {
            throw new IllegalArgumentException("예약 인원은 1명 이상이어야 합니다.");
        }
    }

    private LocalDateTime parseReservedAt(String reservedAtText) {
        try {
            return LocalDateTime.parse(reservedAtText.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("예약 시간 형식이 올바르지 않습니다. ISO-8601 형식을 사용하세요.", e);
        }
    }

    private String generateReservationNumber() {
        return "RSV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getCustomerName(),
                reservation.getReservedAt().toString(),
                reservation.getPartySize(),
                reservation.getStatus().name()
        );
    }
}