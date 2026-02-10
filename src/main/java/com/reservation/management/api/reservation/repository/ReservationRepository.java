package com.reservation.management.api.reservation.repository;

import com.reservation.management.api.reservation.domain.Reservation;
import com.reservation.management.api.reservation.domain.ReservationStatus;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    /**
     * 내부 식별자 기반 단건 조회로 상태 전이(취소/완료) 대상 검증 규칙을 지원합니다.
     */
    @Override
    Optional<Reservation> findById(Long id);

    /**
     * 외부 노출용 예약번호 조회로 고객 조회 시 내부 PK 비노출 규칙을 지원합니다.
     */
    Optional<Reservation> findByReservationNumber(String reservationNumber);

    /**
     * 예약 시각에 활성 상태 예약이 존재하는지 확인해 중복 슬롯 차단 규칙을 지원합니다.
     */
    boolean existsByReservedAtAndStatusIn(LocalDateTime reservedAt, Collection<ReservationStatus> statuses);

    /**
     * 기간 기반 예약 조회로 운영 일정 확인 및 일자별 관리 규칙을 지원합니다.
     */
    List<Reservation> findAllByReservedAtBetween(LocalDateTime from, LocalDateTime to);

    /**
     * 상태별 조회로 취소/확정/노쇼 등 생명주기 상태 관리 규칙을 지원합니다.
     */
    List<Reservation> findAllByStatus(ReservationStatus status);

    /**
     * 기간과 상태 복합 조회로 관리자 필터 검색(예: 특정 기간의 확정 예약) 규칙을 지원합니다.
     */
    List<Reservation> findAllByReservedAtBetweenAndStatusIn(
            LocalDateTime from,
            LocalDateTime to,
            Collection<ReservationStatus> statuses
    );

    /**
     * 상태 집합 + 페이징 조회로 대량 관리자 목록 조회 및 성능 유지 규칙을 지원합니다.
     */
    Page<Reservation> findAllByStatusIn(Collection<ReservationStatus> statuses, Pageable pageable);
}