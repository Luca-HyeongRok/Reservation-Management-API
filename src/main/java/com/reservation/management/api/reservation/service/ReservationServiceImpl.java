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
        // 입력값 검증은 도메인 상태 변경 전에 가장 먼저 처리해, 잘못된 요청이 저장 로직으로 흐르지 않도록 차단합니다.
        validateCreateRequest(request);

        LocalDateTime reservedAt = parseReservedAt(request.reservedAt());

        // 예약 시각은 과거일 수 없으므로, 중복 조회 전에 시간 유효성부터 확인해 불필요한 DB 접근을 줄입니다.
        if (!reservedAt.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("예약 시간은 현재 시각 이후여야 합니다.");
        }

        // 활성 상태(요청/확정) 기준으로 같은 시각이 이미 점유되어 있으면 생성을 막아 슬롯 충돌 규칙을 보장합니다.
        boolean duplicated = reservationRepository.existsByReservedAtAndStatusIn(reservedAt, ACTIVE_STATUSES);
        if (duplicated) {
            throw new IllegalStateException("동일 시간대에 이미 활성 예약이 존재합니다.");
        }

        Reservation reservation = new Reservation();
        reservation.setReservationNumber(generateReservationNumber());
        reservation.setCustomerName(request.customerName().trim());
        // 현재 DTO에는 연락처/이메일 필드가 없어, 엔티티 필수 제약을 만족시키는 기본값을 저장합니다.
        reservation.setCustomerPhone("UNKNOWN");
        reservation.setCustomerEmail(null);
        reservation.setReservedAt(reservedAt);
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
        // 단건 조회는 상태 변경 없이 현재 스냅샷만 반환하므로 읽기 전용 트랜잭션으로 처리합니다.
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("예약을 찾을 수 없습니다. id=" + reservationId));
        return toResponse(reservation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReservationResponse> getReservations() {
        // 목록 조회는 도메인 규칙 적용보다 조회 책임에 집중해 엔티티를 응답 DTO로만 변환합니다.
        return reservationRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public ReservationResponse cancelReservation(Long reservationId) {
        // 취소 대상 조회가 먼저 선행되어야 현재 상태를 근거로 전이 가능 여부를 판정할 수 있습니다.
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new NoSuchElementException("예약을 찾을 수 없습니다. id=" + reservationId));

        ReservationStatus currentStatus = reservation.getStatus();

        // 종결 상태는 취소 전이가 불가능하므로 즉시 예외 처리해 중복 취소/역전이를 차단합니다.
        if (CANNOT_CANCEL_STATUSES.contains(currentStatus)) {
            throw new IllegalStateException("이미 종결된 예약은 취소할 수 없습니다. status=" + currentStatus);
        }

        // 취소 가능한 상태는 요청/확정으로 제한해 도메인 상태 전이 규칙을 강제합니다.
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
                reservation.getStatus().name()
        );
    }
}