# 주문 상태머신과 Saga

## 문서 정보

- 담당자:
- 리뷰어:
- 최종 수정일:
- 상태: Draft | Approved

## 주문 상태

- `PENDING_RESERVE`
- `RESERVE_FAILED`
- `RESERVED`
- `PAYMENT_PENDING`
- `PAYMENT_FAILED`
- `PAID`
- `CONFIRMED`
- `DELIVERED`
- `PURCHASE_CONFIRMED`
- `CANCELLED`
- `REFUND_PENDING`

## 허용 상태 전이

| 현재 상태 | 이벤트/조건 | 다음 상태 | 소유 서비스 |
|---|---|---|---|
| PENDING_RESERVE | StockReserved | RESERVED | order |
| PENDING_RESERVE | StockReserveFailed | RESERVE_FAILED | order |
| RESERVED | PaymentPrepareRequested | PAYMENT_PENDING | order |
| PAYMENT_PENDING | PaymentAuthorized(웹훅 확정) | PAID | order |
| PAYMENT_PENDING | PaymentFailed/Timeout | PAYMENT_FAILED | order |
| PAYMENT_FAILED | InventoryReleaseCompleted | CANCELLED | order |
| PAID | OrderConfirmed | CONFIRMED | order |
| CONFIRMED | OrderDelivered | DELIVERED | order |
| DELIVERED | PurchaseConfirmed | PURCHASE_CONFIRMED | order |
| CONFIRMED | CancelAccepted(환불 프로세스) | REFUND_PENDING | order |
| REFUND_PENDING | RefundSucceeded + InventoryAdjusted | CANCELLED | order |

## 금지 상태 전이

| 현재 상태 | 다음 상태 | 사유 |
|---|---|---|
| CANCELLED | PAID | 종료 상태 |
| PURCHASE_CONFIRMED | CANCELLED | 구매확정 후 자동취소 금지 |
| PAYMENT_PENDING | CONFIRMED | 결제 확정 전 선확정 금지 |
| RESERVE_FAILED | PAID | 재고 실패 주문 결제 확정 금지 |

## Saga 단계 (구매)

1. 주문 생성 (`PENDING_RESERVE`) + `OrderCreated` 이벤트 Outbox 저장(`BEFORE_COMMIT`)
2. Order Saga Orchestrator가 `InventoryReserveRequested` 발행
3. `StockReserved` 수신 시 주문을 `RESERVED`로 전이하고 `PaymentPrepareRequested` 발행
4. Payment Service가 결제 세션 생성 후 클라이언트 PG 결제를 유도
5. PG 웹훅/콜백으로 Payment Service가 `PaymentAuthorized` 또는 `PaymentFailed` 발행
6. Order가 `PaymentAuthorized` 수신 시 `PAID -> CONFIRMED` 전이
7. Order가 `PaymentFailed/Timeout` 수신 시 `InventoryReleaseRequested` 발행 후 `CANCELLED` 수렴
8. 정산/조회모델/알림은 비동기 소비로 후속 반영

## 발행 실패 복구

- `AFTER_COMMIT` 즉시 발행 실패 건은 Outbox `SEND_FAIL`로 저장한다.
- `status != SEND_SUCCESS` && `created_at <= now-10m` 조건으로 배치 재발행한다.
- 재시도 임계치 초과 건은 `DEAD`로 전환하고 운영 알람을 발생시킨다.

## Saga 방식 선택

- 1차: 오케스트레이션(주문 서비스 주도) 방식을 사용한다.
- 이유: 상태 전이 통제가 쉽고, 실패 지점과 보상 경로를 중앙에서 추적하기 용이하다.
- 통신 수단은 Kafka 이벤트를 사용하지만 최종 상태 결정 책임은 Order가 가진다.
- 코레오그래피 전환은 서비스 수가 늘고 운영 성숙도가 올라간 뒤 재검토한다.

## 보상 단계

| 실패 지점 | 보상 동작 |
|---|---|
| 재고 예약 실패 | 주문 `RESERVE_FAILED` 처리 |
| 결제 승인 실패/타임아웃 | `InventoryReleaseRequested` 발행 -> 재고 복원 후 `CANCELLED` |
| 주문 확정 실패 | 결제 상태 재조정 후 정책 기반 롤백 |
| 환불 성공 | 재고/정산 후속 보정 이벤트 반영 |

## 타임아웃 정책

| 단계 | 타임아웃 | 동작 |
|---|---|---|
| 재고 예약 | 3-5초 | 재시도 또는 빠른 실패 |
| 결제 준비 | 10-30초 | 결제 세션 미생성 시 취소 또는 재시도 |
| 결제 승인 대기 | 정책 윈도우 | PG 조회/웹훅으로 정합화 |

## 운영 가드레일

- 결제/재고 동기 경로에 서킷브레이커를 적용한다.
- 동기 검증 실패 시 비동기 보상으로 넘기지 않고 즉시 실패 처리한다.
- 이벤트 소비 지연 시 조회계(마이페이지/판매자 대시보드)만 지연되고 거래 커맨드는 보호되어야 한다.

## 시퀀스 다이어그램

- 다이어그램 위치:
- 최종 검토일:
