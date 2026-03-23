# StablePay Checkout Design (Order Saga Compatible)

기준일: 2026-03-19  
대상 모듈: `order`, `payment`, `api-gateway`, `common`

## 1) 목표

- 구매자 결제수단에 `STABLEPAY`를 추가한다.
- 기존 주문 Saga를 유지하면서, Pay webhook 기반으로 결제 성공/실패를 최종 확정한다.
- 최종 상태 정책은 다음으로 고정한다.
  - 성공: `PaymentAuthorized` 수신 후 주문 `PAID -> CONFIRMED`
  - 실패: `PaymentFailed` 수신 후 주문 `PAYMENT_FAILED`

## 2) 확정 의사결정

- `POST /api/v1/orders` 단일 엔드포인트를 유지한다.
- `OrderCreateRequest.paymentMethod`는 `String`에서 `ENUM(CARD, STABLEPAY)`로 변경한다.
- `CARD`는 기존 mock 결제 흐름을 유지한다.
- `STABLEPAY`는 Pay 세션/웹훅 기반 흐름으로 처리한다.
- Pay webhook은 `payment-service`가 직접 수신한다.
- 1차 webhook 이벤트 범위는 `payment.authorized`, `payment.failed`만 지원한다.
- 세션 발급 실패 시 주문 레코드를 남기고 상태를 `PAYMENT_FAILED`로 전이한다.

## 3) 아키텍처

### 3.1 정상 흐름 (STABLEPAY)

1. FE가 `POST /api/v1/orders` 호출 (`paymentMethod=STABLEPAY`)
2. `order-service`가 주문 생성 + Saga 시작 + 재고 예약 요청
3. `StockReserved` 수신 후 주문 상태를 `PAYMENT_PENDING`으로 전이하고 `PaymentPrepareRequested` 발행
4. `payment-service`가 `PaymentPrepareRequested`를 소비하여 `PENDING` 트랜잭션 생성
5. `order-service`가 내부 오케스트레이션으로 `payment-service`에 세션 준비를 요청하고(내부 API/클라이언트), 응답에 `paymentSessionId` 등을 포함해 FE로 반환
6. FE는 Wallet SDK를 실행하여 결제를 진행
7. Pay -> `payment-service` webhook(`payment.authorized`) 전송
8. `payment-service`는 서명/멱등 검증 후 `PaymentAuthorized` 도메인 이벤트 발행
9. `order-service`가 기존 Saga 로직으로 `PAID -> CONFIRMED` 전이

### 3.2 실패 흐름

- Pay webhook이 `payment.failed`면 `payment-service`가 `PaymentFailed` 이벤트 발행
- `order-service`는 `PAYMENT_PENDING -> PAYMENT_FAILED`로 전이
- 정책상 최종 상태는 `PAYMENT_FAILED`로 유지한다.

## 4) 데이터 모델

### 4.1 Order

- `order` 도메인에 `PaymentMethod` enum 도입
  - `CARD`
  - `STABLEPAY`
- DB는 문자열(`VARCHAR`) 저장을 유지하고 JPA enum string 매핑을 사용한다.

### 4.2 Payment

`payment_transaction`에 stablepay 추적 필드를 추가한다.

- `payment_key` (nullable, unique)
- `payment_session_id` (nullable)
- `merchant_key` (nullable)
- `payer_address` (nullable)
- `token_address` (nullable)
- `command_id` (nullable)
- `command_status` (nullable)
- `tx_hash` (nullable)

### 4.3 Webhook Dedupe

신규 테이블 예시: `payment_webhook_event`

- `event_id` (PK/UK)
- `event_type`
- `occurred_at`
- `received_at`
- `payload_json`
- `payload_hash`

`event_id`가 이미 처리된 경우 200 응답 후 재처리하지 않는다.

## 5) API 계약

## 5.1 외부 API (Buyer)

- 기존 `POST /api/v1/orders` 응답에 stablepay 세션 정보를 선택적으로 포함
  - `paymentSessionId`
  - `paymentKey`
  - `paymentId`
  - `merchantKey`
  - `nonce`
  - `deadline`
  - `payloadHash`

`CARD`일 때는 기존 응답 스키마를 유지한다.

### 5.2 내부 API (Order -> Payment)

- `order-service`가 `payment-service`에 stablepay 세션 준비를 요청하는 내부 API를 추가
- 요청에는 주문 식별자, 결제 금액/토큰/지갑 정보, 멱등 키를 포함한다.

### 5.3 Webhook API (Pay -> Payment)

- `POST /api/v1/payments/webhooks/pay`
- 검증 헤더
  - `X-Webhook-Timestamp`
  - `X-Webhook-Signature`
- 서명 규칙
  - 원문: `timestamp + "." + rawBody`
  - 알고리즘: HMAC-SHA256
  - 허용 오차: ±300초

## 6) 멱등성

- 주문 생성 요청: 기존 주문 멱등 정책 유지
- Order -> Payment 내부 세션 준비: `Idempotency-Key` 강제
- webhook 처리: `eventId` 유니크 멱등
- Payment -> Order 이벤트 소비: 기존 inbox 멱등 유지

## 7) 오류/운영 정책

- STABLEPAY 세션 발급 실패:
  - payment transaction 상태 `FAILED`
  - `PaymentFailed` 이벤트 발행
  - 주문 상태 `PAYMENT_FAILED` 수렴
- webhook 검증 실패:
  - 400 응답, 비즈니스 상태 변경 금지
- 관측성:
  - webhook 수신/검증 실패/중복 카운터
  - stablepay 세션 발급 성공/실패 메트릭

## 8) 테스트 전략

- 단위 테스트
  - payment method enum 파싱/검증
  - webhook 서명 검증
  - webhook 멱등 처리
  - stablepay 세션 실패 시 `PaymentFailed` 발행
- 통합 테스트
  - `POST /orders (STABLEPAY)`에서 세션 정보 반환
  - `payment.authorized` webhook -> 주문 `CONFIRMED`
  - `payment.failed` webhook -> 주문 `PAYMENT_FAILED`

## 9) 점진 배포

1. DB 마이그레이션 + 코드 배포 (feature off)
2. `STABLEPAY` 결제수단 노출 (내부/스테이징)
3. webhook 검증/멱등 모니터링 확인
4. 점진 트래픽 확대
