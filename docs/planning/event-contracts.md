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
| cdc-events | aggregateId | CDC 파이프라인 | 검색/캐시/조회모델 | 파티션 정책 별도 정의 |

## 이벤트 정의

| 이벤트 타입 | 트리거 | 최소 Payload |
|---|---|---|
| OrderCreated | 주문 생성 | orderId, buyerId, lines, totalAmount |
| StockReserved | 재고 예약 성공 | orderId, reservationId, lines |
| StockReserveFailed | 재고 예약 실패 | orderId, reasonCode |
| PaymentAuthorized | 결제 승인 성공 | orderId, paymentId, amount, method |
| PaymentAuthorizeFailed | 결제 승인 실패 | orderId, reasonCode |
| PaymentConfirmationPending | 결제 결과 미확정 | orderId, paymentId, timeoutAt |
| RefundSucceeded | 환불 성공 | orderId, refundId, amount |
| OrderConfirmed | 주문 확정 | orderId, confirmedAt |
| OrderCancelled | 주문 취소 완료 | orderId, cancelledAt |

## 호환성 정책

- 하위 호환 규칙:
- 스키마 레지스트리 사용 여부:
- 이벤트 폐기 유예 기간:
- 소비자 업그레이드 전략:

## 전달/재시도 정책

- Producer 보장 수준:
- Consumer 보장 수준:
- 재시도 횟수:
- DLQ 토픽:
- 리플레이 담당자:

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
| 주문/결제 최종 반영 지연 | N초 이내 (서비스별 확정) |
| 판매자 주문 대시보드 지연 | N초 이내 (피크 제외) |
| 재고 조회계 지연 | N초 이내 (피크 제외) |
