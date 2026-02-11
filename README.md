# Reservation Management API

Spring Boot 기반 예약 관리 API 프로젝트입니다.  
단순 CRUD를 넘어서 예약의 상태 전이와 정책 검증을 중심으로 설계해, 확장 가능한 백엔드 구조를 목표로 했습니다.

## 1. 프로젝트 개요

이 프로젝트는 예약 생성, 조회, 취소 기능을 API로 제공합니다.  
핵심 목적은 다음과 같습니다.

- 예약 생명주기(`REQUESTED -> CONFIRMED -> COMPLETED`, 취소/노쇼 분기)를 명시적으로 관리
- 잘못된 요청, 정책 위반, 미존재 리소스를 HTTP 상태 코드로 일관되게 반환
- 컨트롤러는 얇게 유지하고 비즈니스 규칙은 서비스 계층에 집중

## 2. 기술 스택

- Java 17
- Spring Boot 3.5.0
- Spring Web
- Spring Data JPA
- H2 Database (in-memory, local default)
- MySQL (runtime profile)
- Maven

## 3. 패키지 구조

```text
src/main/java/com/reservation/management/api
├─ reservation
│  ├─ controller
│  │  └─ ReservationController.java
│  ├─ service
│  │  ├─ ReservationService.java
│  │  └─ ReservationServiceImpl.java
│  ├─ repository
│  │  └─ ReservationRepository.java
│  ├─ domain
│  │  ├─ Reservation.java
│  │  └─ ReservationStatus.java
│  └─ dto
│     ├─ ReservationCreateRequest.java
│     └─ ReservationResponse.java
└─ global
   └─ exception
      ├─ ErrorResponse.java
      └─ GlobalExceptionHandler.java
```

구조적 장점:
- `controller`: HTTP 입출력 매핑만 담당
- `service`: 비즈니스 규칙/상태 전이 책임 집중
- `repository`: 데이터 접근 추상화
- `domain`: 핵심 모델과 상태 정의
- `global/exception`: 예외 응답 정책 일원화

## 4. 예약 도메인 설계 및 상태 전이

### Reservation 핵심 필드
- `id`: 내부 식별자
- `reservationNumber`: 외부 노출용 예약 번호
- `customerName`, `customerPhone`, `customerEmail`: 예약자 정보
- `reservedAt`: 실제 예약 시각
- `status`: 예약 상태(`ReservationStatus`)
- `cancelReason`: 취소 사유
- `createdAt`, `updatedAt`: 생성/수정 시점
- `version`: 동시성 제어(낙관적 락)

### ReservationStatus
- `REQUESTED`: 예약 요청 접수
- `CONFIRMED`: 예약 확정
- `CANCELED`: 취소 완료(종결)
- `COMPLETED`: 이용 완료(종결)
- `NO_SHOW`: 미방문 종결(종결)

### 상태 전이 규칙
- 가능:
  - `REQUESTED -> CONFIRMED`
  - `REQUESTED -> CANCELED`
  - `CONFIRMED -> CANCELED`
  - `CONFIRMED -> COMPLETED`
  - `CONFIRMED -> NO_SHOW`
- 불가:
  - `CANCELED`, `COMPLETED`, `NO_SHOW`에서 다른 상태로 전이
  - `REQUESTED -> COMPLETED/NO_SHOW`

### 현재 서비스 로직에 반영된 핵심 정책
- 예약 생성:
  - 필수값 검증
  - 예약 시각은 현재 이후만 허용
  - 활성 상태(`REQUESTED`, `CONFIRMED`) 기준 중복 시간대 차단
  - 초기 상태는 `REQUESTED`
- 예약 취소:
  - `REQUESTED`, `CONFIRMED`에서만 허용
  - `CANCELED`, `COMPLETED`, `NO_SHOW`는 예외 처리

## 5. 예외 처리 전략

`@RestControllerAdvice`를 통해 예외를 공통 형식으로 반환합니다.

- 공통 응답 DTO: `ErrorResponse`
  - 필드: `message`

예외 매핑:

| 예외 타입 | HTTP 상태 | 의미 |
|---|---|---|
| `IllegalArgumentException` | `400 Bad Request` | 필수값 누락, 형식 오류 등 잘못된 요청 |
| `NoSuchElementException` | `404 Not Found` | 조회 대상 없음 |
| `IllegalStateException` | `409 Conflict` | 상태 전이 불가, 중복 예약 등 정책 위반 |
| `Exception` | `500 Internal Server Error` | 기타 서버 내부 오류 |

장점:
- 클라이언트가 에러 형식을 일관되게 처리 가능
- 도메인 정책 위반과 단순 입력 오류를 HTTP 레벨에서 명확히 구분

## 6. 주요 API 엔드포인트 요약

기본 경로: `/api/reservations`

| Method | URI | 설명 |
|---|---|---|
| `POST` | `/api/reservations` | 예약 생성 |
| `GET` | `/api/reservations/{reservationId}` | 예약 단건 조회 |
| `GET` | `/api/reservations` | 예약 목록 조회 |
| `PATCH` | `/api/reservations/{reservationId}/cancel` | 예약 취소 |

### 요청/응답 DTO
- 생성 요청: `ReservationCreateRequest`
  - `customerName`
  - `reservedAt` (ISO-8601 문자열)
- 응답: `ReservationResponse`
  - `reservationId`
  - `customerName`
  - `reservedAt`
  - `status`

---

이 프로젝트는 기능 추가 시에도 `도메인 규칙 -> 서비스 -> HTTP 매핑` 흐름을 유지하도록 설계되어,  
결제/승인/관리자 기능 확장 시 구조적 일관성을 유지하기 쉽습니다.

## 7. 실행 방법

### 1) 기본 실행 (H2 in-memory, 기본 프로필)
```bash
mvn spring-boot:run
```

### 2) MySQL 프로필로 실행
사전 준비:
- MySQL DB 생성
```sql
CREATE DATABASE reservation_management CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

- 환경변수 설정 예시 (PowerShell)
```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/reservation_management?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="비밀번호"
```

실행:
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=mysql
```

### 3) 서버 확인
- 기본 주소: `http://localhost:8080`
- 예약 API 기본 경로: `http://localhost:8080/api/reservations`
- H2 콘솔(H2 프로필일 때만): `http://localhost:8080/h2-console`

## 8. API 요청 예시 (JSON)

### 1) 예약 생성
- `POST /api/reservations`

요청:
```json
{
  "customerName": "홍길동",
  "reservedAt": "2026-02-20T14:30:00"
}
```

성공 응답 예시 (`201 Created`):
```json
{
  "reservationId": 1,
  "customerName": "홍길동",
  "reservedAt": "2026-02-20T14:30",
  "status": "REQUESTED"
}
```

### 2) 예약 단건 조회
- `GET /api/reservations/{reservationId}`

응답 예시 (`200 OK`):
```json
{
  "reservationId": 1,
  "customerName": "홍길동",
  "reservedAt": "2026-02-20T14:30",
  "status": "REQUESTED"
}
```

### 3) 예약 취소
- `PATCH /api/reservations/{reservationId}/cancel`

응답 예시 (`200 OK`):
```json
{
  "reservationId": 1,
  "customerName": "홍길동",
  "reservedAt": "2026-02-20T14:30",
  "status": "CANCELED"
}
```

## 9. 에러 응답 예시

공통 형식:
```json
{
  "message": "에러 메시지"
}
```

예시 1) 잘못된 요청 (`400 Bad Request`)
```json
{
  "message": "예약 시간은 현재 시각 이후여야 합니다."
}
```

예시 2) 리소스 없음 (`404 Not Found`)
```json
{
  "message": "예약을 찾을 수 없습니다. id=999"
}
```

예시 3) 정책 위반 (`409 Conflict`)
```json
{
  "message": "동일 시간대에 이미 활성 예약이 존재합니다."
}
```
