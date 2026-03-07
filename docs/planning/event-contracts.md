# 이벤트 계약 (Kafka)

## 문서 정보

- 담당자:
- 리뷰어:
- 최종 수정일:
- 상태: Draft | Approved

## Envelope 표준

| 필드 | 타입 | 필수 | 설명 |
|---|---|---|---|
| eventId | string | Y | 전역 고유 이벤트 ID |
| eventType | string | Y | 의미 기반 이벤트 이름 |
| aggregateType | string | Y | Order/Payment/Inventory/Settlement |
| aggregateId | string | Y | 집합체 식별자 |
| occurredAt | datetime | Y | UTC 발생 시각 |
| version | integer | Y | 스키마 버전 |
| traceId | string | Y | 분산 추적 상관키 |
| producer | string | Y | 발행 서비스명 |
| payload | object | Y | 이벤트 본문 |

## 토픽 설계

| 토픽 | 키 | 생산자 | 소비자 | 순서 보장 필요 |
|---|---|---|---|---|
| order-events | orderId | order | payment, inventory, settlement, read-model | 주문 단위 엄격 |
| inventory-events | orderId | inventory | order, read-model | 주문 단위 엄격 |
| payment-events | orderId | payment | order, settlement, read-model | 주문 단위 엄격 |
| settlement-events | sellerId | settlement | seller read-model | 판매자 단위 권장 |
| payment-webhook-events | paymentKey | payment | order, read-model | 결제 단위 권장 |

## 이벤트 전달 모델 (표준)

| 항목 | 정책 |
|---|---|
| Producer 쓰기 | 도메인 변경 + Outbox insert를 동일 DB 트랜잭션으로 처리 |
| Producer 발행 | `@TransactionalEventListener(AFTER_COMMIT)`에서 Kafka 즉시 발행 시도 |
| Kafka 전달 보장 | At-Least-Once |
| Consumer 처리 | Inbox unique(eventId, consumer) + 멱등 처리 |
| Offset Commit | 소비 비즈니스 트랜잭션 성공 후 commit |
| 장애 시 처리 | 재시도 후 DLQ 이동, 운영 승인 후 리플레이 |

## 발행 구현 결정(현재)

- 현재: `BEFORE_COMMIT` Outbox 저장 + `AFTER_COMMIT` 즉시 발행 + 10분 경과 재발행 배치
- 보류: Outbox Relay 완전 분리 여부는 추후 ADR에서 결정

## 재고 서비스 소비 구현(현재)

- `InventoryReserveRequested` / `InventoryReleaseRequested`를 `wearhouse.inventory.command.v1`에서 소비
- Inbox unique(`eventId`, `consumer`)로 중복 소비 차단
- 처리 성공 후 `wearhouse.inventory.event.v1`에 결과 이벤트 발행

## Outbox 기록/발행 시점

| 항목 | 정책 |
|---|---|
| Outbox 저장 | `@TransactionalEventListener(BEFORE_COMMIT)`에서 Outbox insert |
| 즉시 발행 | `@TransactionalEventListener(AFTER_COMMIT)`에서 Kafka 발행 |
| 즉시 발행 실패 | Outbox 상태 `SEND_FAIL` 기록 후 배치 재발행 대상으로 전환 |
| 최종 성공 상태 | Outbox 상태 `SEND_SUCCESS` |
| 실패 중단 상태 | Outbox 상태 `DEAD` + 운영 알람 |

## Outbox 배치 재발행 정책

| 항목 | 정책 |
|---|---|
| 실행 주기 | 1분 |
| 대상 조건 | `status != SEND_SUCCESS` 이고 `created_at <= NOW() - 10분` |
| 조회 단위 | 1회 500건 (서비스별 조정) |
| 락 전략 | `FOR UPDATE SKIP LOCKED` |
| 성공 처리 | `status=SEND_SUCCESS`, `published_at` 기록 |
| 실패 처리 | `status=SEND_FAIL`, `retry_count+1`, `next_retry_at` 갱신 |
| 중단 기준 | `retry_count` 임계치 초과 시 `DEAD` 전환 |

예시 조회 SQL:

```sql
SELECT id, event_id, topic, partition_key, payload, retry_count
FROM order_outbox_event
WHERE status <> 'SEND_SUCCESS'
  AND created_at <= NOW() - INTERVAL 10 MINUTE
  AND (next_retry_at IS NULL OR next_retry_at <= NOW())
ORDER BY id
LIMIT 500
FOR UPDATE SKIP LOCKED;
```

## 이벤트 정의

| 이벤트 타입 | 트리거 | 최소 Payload |
|---|---|---|
| OrderCreated | 주문 생성 | orderId, buyerId, lines, totalAmount |
| PaymentPrepareRequested | 재고 예약 성공 후 결제 준비 요청 | orderId, buyerId, amount, methodOptions |
| PaymentSessionCreated | 결제 세션 생성 완료 | orderId, paymentKey, expiresAt |
| StockReserved | 재고 예약 성공 | orderId, reservationId, lines |
| StockReserveFailed | 재고 예약 실패 | orderId, reasonCode |
| PaymentAuthorized | PG 웹훅/재조회로 결제 승인 확정 | orderId, paymentId, paymentKey, amount, method |
| PaymentFailed | PG 웹훅/재조회로 결제 실패 확정 | orderId, paymentId, paymentKey, reasonCode |
| PaymentConfirmationPending | 결제 결과 미확정 | orderId, paymentId, timeoutAt |
| InventoryReleaseRequested | 결제 실패/취소로 재고 복구 요청 | orderId, reasonCode |
| InventoryReleased | 재고 복구 완료 | orderId, releaseId |
| RefundSucceeded | 환불 성공 | orderId, refundId, amount |
| OrderConfirmed | 주문 확정 | orderId, confirmedAt |
| OrderCancelled | 주문 취소 완료 | orderId, cancelledAt |
| OrderDelivered | 배송 완료 | orderId, deliveredAt |
| PurchaseConfirmed | 구매 확정 | orderId, purchaseConfirmedAt |

## 호환성 정책

- 하위 호환 규칙: 필드 추가 중심(삭제/의미변경 금지), 기본값으로 해석 가능해야 함
- 스키마 레지스트리 사용 여부: 1차 미도입(JSON), 2차 도입 검토
- 이벤트 폐기 유예 기간: 최소 2 릴리스(또는 4주) 공지 후 폐기
- 소비자 업그레이드 전략: producer 선배포 -> consumer 점진 전환 -> 구버전 제거

## 전달/재시도 정책

- Producer 보장 수준: Outbox + 트랜잭션 이벤트 리스너 기반 At-Least-Once
- Consumer 보장 수준: Inbox 기반 effectively-once
- 재시도 횟수: 즉시 3회 + 지수 백오프 재시도(최대 20분)
- DLQ 토픽: `<원본토픽>.dlq`
- 리플레이 담당자: 서비스 오너 + 온콜 승인

## 소비 멱등/중복 방지

| 항목 | 정책 |
|---|---|
| 중복 이벤트 판별 키 | `eventId` |
| 1차 중복 방지 | Redis `SETNX` (단기) |
| 2차 중복 방지 | Inbox 테이블 unique(eventId, consumer) |
| 실패 메시지 처리 | 재시도 후 DLQ 이동 |
| DLQ 재처리 | 수동 승인 + 자동 검증 후 리플레이 |

## 최종 일관성 목표

| 항목 | 목표 |
|---|---|
| 주문/결제 최종 반영 지연 | 평시 5초 이내 / 피크 30초 이내 |
| 판매자 주문 대시보드 지연 | 평시 3초 이내 / 피크 20초 이내 |
| 재고 조회계 지연 | 평시 2초 이내 / 피크 10초 이내 |
