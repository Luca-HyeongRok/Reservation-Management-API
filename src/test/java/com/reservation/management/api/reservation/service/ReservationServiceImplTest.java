package com.reservation.management.api.reservation.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.reservation.management.api.reservation.domain.Reservation;
import com.reservation.management.api.reservation.domain.ReservationStatus;
import com.reservation.management.api.reservation.dto.ReservationCreateRequest;
import com.reservation.management.api.reservation.dto.ReservationResponse;
import com.reservation.management.api.reservation.repository.ReservationRepository;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ReservationServiceImplTest {

    @Mock
    private ReservationRepository reservationRepository;

    private ReservationServiceImpl reservationService;

    @BeforeEach
    void setUp() {
        reservationService = new ReservationServiceImpl(reservationRepository);
    }

    @Test
    @DisplayName("예약 생성 성공")
    void createReservation_success_savesAsRequested() {
        LocalDateTime futureTime = LocalDateTime.now().plusDays(1);
        ReservationCreateRequest request = new ReservationCreateRequest("홍길동", futureTime.toString(), 2);

        when(reservationRepository.existsByReservedAtAndStatusIn(
                eq(futureTime),
                eq(EnumSet.of(ReservationStatus.REQUESTED, ReservationStatus.CONFIRMED))
        )).thenReturn(false);

        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> {
            Reservation saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });

        ReservationResponse response = reservationService.createReservation(request);

        assertEquals(1L, response.reservationId());
        assertEquals("홍길동", response.customerName());
        assertEquals(2, response.partySize());
        assertEquals("REQUESTED", response.status());
        verify(reservationRepository).save(any(Reservation.class));
    }

    @Test
    @DisplayName("예약 생성 실패: 과거 시간")
    void createReservation_fail_whenReservedAtIsPast() {
        LocalDateTime pastTime = LocalDateTime.now().minusMinutes(1);
        ReservationCreateRequest request = new ReservationCreateRequest("홍길동", pastTime.toString(), 2);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reservationService.createReservation(request)
        );

        assertTrue(exception.getMessage().contains("예약 시간"));
        verify(reservationRepository, never()).existsByReservedAtAndStatusIn(any(), any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 실패: 동일 시간 중복")
    void createReservation_fail_whenActiveReservationExistsAtSameTime() {
        LocalDateTime futureTime = LocalDateTime.now().plusHours(3);
        ReservationCreateRequest request = new ReservationCreateRequest("홍길동", futureTime.toString(), 2);

        when(reservationRepository.existsByReservedAtAndStatusIn(
                eq(futureTime),
                eq(EnumSet.of(ReservationStatus.REQUESTED, ReservationStatus.CONFIRMED))
        )).thenReturn(true);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reservationService.createReservation(request)
        );

        assertTrue(exception.getMessage().contains("동일 시간대"));
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 생성 실패: partySize가 1 미만")
    void createReservation_fail_whenPartySizeLessThanOne() {
        LocalDateTime futureTime = LocalDateTime.now().plusHours(3);
        ReservationCreateRequest request = new ReservationCreateRequest("홍길동", futureTime.toString(), 0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> reservationService.createReservation(request)
        );

        assertEquals("예약 인원은 1명 이상이어야 합니다.", exception.getMessage());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("예약 취소 성공")
    void cancelReservation_success_whenStatusIsRequested() {
        Reservation reservation = createReservation(10L, ReservationStatus.REQUESTED, LocalDateTime.now().plusDays(1));
        LocalDateTime beforeCancelUpdateTime = reservation.getUpdatedAt();

        when(reservationRepository.findById(10L)).thenReturn(Optional.of(reservation));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ReservationResponse response = reservationService.cancelReservation(10L);

        assertEquals("CANCELED", response.status());
        assertEquals(ReservationStatus.CANCELED, reservation.getStatus());
        assertNotNull(reservation.getCancelReason());
        assertTrue(reservation.getUpdatedAt().isAfter(beforeCancelUpdateTime));
        verify(reservationRepository).save(reservation);
    }

    @ParameterizedTest(name = "예약 취소 실패: {0} 상태")
    @MethodSource("nonCancelableStatuses")
    void cancelReservation_fail_whenStatusIsFinalized(ReservationStatus finalizedStatus) {
        Reservation reservation = createReservation(20L, finalizedStatus, LocalDateTime.now().plusDays(1));
        when(reservationRepository.findById(20L)).thenReturn(Optional.of(reservation));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> reservationService.cancelReservation(20L)
        );

        assertTrue(exception.getMessage().contains("취소"));
        verify(reservationRepository, never()).save(any());
    }

    private static Stream<Arguments> nonCancelableStatuses() {
        return Stream.of(
                Arguments.of(ReservationStatus.CANCELED),
                Arguments.of(ReservationStatus.COMPLETED)
        );
    }

    private Reservation createReservation(Long id, ReservationStatus status, LocalDateTime reservedAt) {
        Reservation reservation = new Reservation();
        ReflectionTestUtils.setField(reservation, "id", id);
        reservation.setReservationNumber("RSV-TEST-" + id);
        reservation.setCustomerName("테스트");
        reservation.setCustomerPhone("010-0000-0000");
        reservation.setCustomerEmail("test@example.com");
        reservation.setReservedAt(reservedAt);
        reservation.setPartySize(2);
        reservation.setStatus(status);
        reservation.setCancelReason(null);
        reservation.setCreatedAt(LocalDateTime.now().minusHours(2));
        reservation.setUpdatedAt(LocalDateTime.now().minusHours(1));
        return reservation;
    }
}