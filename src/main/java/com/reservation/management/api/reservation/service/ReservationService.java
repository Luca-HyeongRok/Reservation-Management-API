package com.reservation.management.api.reservation.service;

import com.reservation.management.api.reservation.dto.ReservationCreateRequest;
import com.reservation.management.api.reservation.dto.ReservationResponse;
import java.util.List;

/**
 * 예약 유스케이스 계약입니다.
 */
public interface ReservationService {

    /**
     * 클라이언트 요청으로 새 예약을 생성합니다.
     */
    ReservationResponse createReservation(ReservationCreateRequest request);

    /**
     * 예약 ID로 단건 예약을 조회합니다.
     */
    ReservationResponse getReservation(Long reservationId);

    /**
     * 전체 예약 목록을 조회합니다.
     */
    List<ReservationResponse> getReservations();

    /**
     * 기존 예약을 취소합니다.
     */
    ReservationResponse cancelReservation(Long reservationId);
}
