# 재고 동시성 정책

## 문서 정보

- 담당자: inventory-service
- 리뷰어:
- 최종 수정일: 2026-03-06
- 상태: Draft

## 목표

- 고동시성 환경에서 품절 이상 판매 방지
- 재고 예약과 결제 결과 정합성 유지
- 타임아웃/장애 상황의 결정적 복구 보장
- 모드별 운영 기준은 `traffic-mode-strategy.md`를 따른다.

## 단일 진실 원천

- System of Record: inventory DB
- 캐시 역할: 조회 가속 전용(최종 권위 아님)

## 예약 알고리즘

1. SKU/수량 검증
2. 캐시 계층에서 원자 연산으로 1차 수량 방어(선차감/검증)
3. 임계 구간 분산락으로 동시 갱신 충돌 축소
4. DB 원자적 조건부 갱신 + version 검증으로 최종 확정
5. 성공 시 `reserved` 증가, `available` 감소
6. 예약 레코드(TTL 포함) 저장
7. `StockReserved` 이벤트 발행

## 구현 반영 (Phase 1)

### 예약 시작 시점

- 장바구니/주문서 진입 단계:
  - Redis 기반 재고 "조회/예측"만 수행(하드 예약 없음)
- `결제하기` 버튼 클릭 단계:
  - `InventoryReserveRequested` 수신 후 실제 DB 하드 예약 수행
  - 실패 시 `StockReserveFailed`, 성공 시 `StockReserved` 발행

### 현재 구현된 방어선

1. Inbox 멱등성:
  - `inventory_inbox_event` unique(`event_id`, `consumer_name`)로 중복 소비 차단
2. DB 동시성 제어:
  - `inventory_stock.version` 낙관적 락 기반 충돌 감지
  - 충돌 시 제한 재시도 후 실패 이벤트 발행
3. 예약/복구 원장:
  - `inventory_reservation`에 예약 행 저장
  - `InventoryReleaseRequested` 수신 시 RESERVED 행만 RELEASED로 전환하고 수량 복구
4. 이벤트 응답:
  - 성공: `StockReserved`, `InventoryReleased`
  - 실패: `StockReserveFailed`

### 트랜잭션 경계

- `InventoryReserveRequested` 처리:
  - 재고 차감 + 예약행 저장 + 도메인이벤트 생성을 단일 트랜잭션으로 처리
  - `AFTER_COMMIT`에서 Kafka 발행
- `InventoryReleaseRequested` 처리:
  - 예약행 상태 전환 + 재고 복구를 단일 트랜잭션으로 처리
  - `AFTER_COMMIT`에서 `InventoryReleased` 발행

## SQL/락 전략

| 방식 | 사용 시점 | 비고 |
|---|---|---|
| 조건부 업데이트 | 기본 | 가장 빠른 안전 경로 |
| 낙관적 락(version) | 충돌 감지 | 제한 재시도 |
| 분산락(선택) | 핫 SKU 완충 | DB 검증 대체 금지 |
| Redis 원자 연산 | 선검증/선차감 | DB 최종 검증 필수 |

## 예약 TTL 정책

| 항목 | 값 |
|---|---|
| 기본 TTL | 15분 |
| 연장 정책 | 없음 또는 제한적 허용 |
| 만료 워커 주기 | 30-60초 |
| 만료 처리 | reserved를 available로 복원 |

## 캐시/원장 정합성 정책

| 항목 | 정책 |
|---|---|
| 기준 데이터 | DB 원장 |
| Redis 장애 시 | DB 직조회 + 제한 모드 |
| 불일치 감지 | 실시간 샘플링 + 일 배치 대사 |
| 불일치 조치 | DB 기준 캐시 재적재 |

## 외부 연동(ERP/WMS) 정책

| 상황 | 동작 |
|---|---|
| ERP 반영 지연 | 내부 원장 우선 처리 후 비동기 재전송 |
| ERP 장애 | 지연 큐 적재 + 재시도 + 운영 알림 |
| 대량 동기화 | 배치 API 경로 분리 운영 |

## 확정/해제 규칙

| 트리거 | 재고 동작 |
|---|---|
| 결제 성공 | `reserved -> sold` |
| 결제 실패 | `reserved -> available` |
| 주문 취소 | 출고 상태 기준으로 `reserved/sold` 조정 |

## 엣지 케이스

- 중복 예약 커맨드:
- 결제 지연 성공(타임아웃 이후):
- 부분 배송 후 취소:
- 캐시 Stampede:
- 검색/조회 저장소 장애 시 폴백:
  이벤트 계약(고정)
1.
InventoryReserveRequested
2.
StockReserved
3.
StockReserveFailed
4.
PaymentPrepareRequested
5.
PaymentAuthorized
6.
PaymentFailed
7.
InventoryReleaseRequested
8.
InventoryReleased
9.
OrderConfirmed
10.
OrderCancelled
구현 순서
1.
Inventory 예약/해제 도메인 + 락/낙관락 구현.
2.
Order <-> Inventory Saga 연동 완료.
3.
Payment Mock 먼저 구현(승인/실패 이벤트).
4.
보상 트랜잭션 + 타임아웃 배치 완성.
5.
이후 PG 실연동(웹훅 멱등/서명검증 포함).
6.
Grafana에 reserve success rate, reserve fail reason, dead outbox, payment fail rate 패널 추가.