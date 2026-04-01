# Inventory Lock ON/OFF 비교 테스트 (재고 탈취 검증)

- 날짜: 2026-03-31 (KST)
- 환경: EKS `wearhouse-eks` (`ap-northeast-2`)
- 대상 SKU: `sku_id=250`
- 목적: 재고 락 적용 여부에 따라 재고 탈취(초과 예약/음수 재고) 발생 여부 비교

## 1) 공통 테스트 조건

- 주문 API: `POST /order-service/api/v1/buyer/guest/orders`
- k6 시나리오: `scripts/loadtest/order-checkout-no-confirm.k6.js`
- 부하: `RATE=120`, `VUS=300`, `DURATION=1m`, `OPTION_IDS=250`
- 테스트 전 매 케이스 공통 초기화:
  - `inventory_stock(sku=250)` -> `available_qty=30`, `reserved_qty=0`, `product_status=RELEASED`
  - `inventory_reservation where sku_id=250` 전량 삭제

## 2) 케이스 정의

- Lock ON: `INVENTORY_HOT_SKUS=250`
- Lock OFF: `INVENTORY_HOT_SKUS=999999` (테스트 SKU 미포함 -> 실질 락 미적용)

## 3) 결과 요약

| 항목 | Lock ON | Lock OFF |
|---|---:|---:|
| HTTP 요청 수 | 2,845 | 3,322 |
| 처리량(req/s) | 44.12 | 51.45 |
| 평균 지연(ms) | 6,414.93 | 5,464.06 |
| p95(ms) | 14,659.53 | 7,357.16 |
| p99(ms) | 16,450.17 | 7,806.09 |
| HTTP 실패율 | 0.1757% | 0.00% |
| dropped iterations | 4,356 | 3,879 |

## 4) 재고 탈취 검증 결과

### 최종 DB 상태 (공통)

- `inventory_stock(sku=250)`: `available_qty=0`, `reserved_qty=30`, `product_status=SOLD_OUT`
- `inventory_reservation(sku=250, status=RESERVED)`:
  - `reserved_sum=30`
  - `reserved_count=30`

### 판정

- 두 케이스 모두 **초과 예약 없음**
  - `reserved_sum(30) <= 초기재고(30)`
  - `available_qty` 음수 미발생
- 즉, 이번 실험 범위에서는 **재고 탈취(oversell) 재현 실패**.

## 5) 메트릭 델타 (inventory-service)

| 메트릭 델타 | Lock ON | Lock OFF |
|---|---:|---:|
| `lock_success` | +1,048 | +0 |
| `lock_timeout` | +10 | +0 |
| `reserve_requested` | +1,063 | +3,427 |
| `reserve_reserved` | +30 | +30 |
| `reserve_failed_oos (inventory_409_001)` | +1,018 | +3,391 |
| `reserve_failed_lock (inventory_409_003)` | +10 | +0 |

## 6) 해석

- Lock ON은 락 획득/타임아웃 경로가 활성화되어 보호 계층이 동작함.
- Lock OFF는 락 경합 비용이 없어 지연/처리량이 더 유리했음.
- 다만 재고 보호는 DB 측 동시성 제어(조건부 업데이트/낙관적 락)로도 유지되어 초과예약은 발생하지 않았음.
- 결론적으로:
  - **정합성(oversell 방지)**: ON/OFF 모두 확보
  - **지연/처리량**: OFF가 유리
  - **락 기반 보호 신호/제어**: ON에서만 확보

## 7) 원본 파일

- k6 결과
  - `docs/perf/raw/lock-compare-on-k6.log`
  - `docs/perf/raw/lock-compare-on-k6.json`
  - `docs/perf/raw/lock-compare-off-k6.log`
  - `docs/perf/raw/lock-compare-off-k6.json`
- 메트릭 스냅샷
  - `docs/perf/raw/inventory-lock-compare-on-before.prom`
  - `docs/perf/raw/inventory-lock-compare-on-after.prom`
  - `docs/perf/raw/inventory-lock-compare-off-before.prom`
  - `docs/perf/raw/inventory-lock-compare-off-after.prom`
