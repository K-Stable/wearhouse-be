# Order E2E (No Confirm) Load Test Report

- Date: 2026-03-30 (KST)
- Environment: EKS `wearhouse-eks` (ap-northeast-2)
- Flow: `buyer login -> create order -> (if success) get order detail`
- Excluded: payment `confirm`
- Target SKU: `productId=1`, `optionId=1`

## 1) Main Run (4 minutes, ramping 5->10->25->40 VU)

### k6 summary

| Metric | Value |
|---|---:|
| Iterations | 4,596 |
| Iteration rate | 19.12/s |
| HTTP requests | 4,607 |
| Request rate | 19.17/s |
| HTTP avg latency | 1,133 ms |
| HTTP p95 latency | 1,773 ms |
| HTTP max latency | 6,447 ms |
| Order create success | 10 |
| Order create failure | 4,586 |
| Status = PAYMENT_PENDING | 10 |
| E2E error rate | 99.78% |

### Service metrics delta (before/after)

| Metric | Delta |
|---|---:|
| order POST `/api/v1/buyer/orders` 200 | +10 |
| order POST `/api/v1/buyer/orders` 409 | +4,586 |
| `wearhouse_order_inventory_reserve_result_total{requested}` | +4,596 |
| `wearhouse_order_inventory_reserve_result_total{reserved}` | +10 |
| `wearhouse_order_inventory_reserve_result_total{failed}` | +4,586 |
| order kafka listener success (sum) | +4,596 |
| order kafka template success | +4,606 |
| inventory kafka listener success (sum) | +4,596 |
| inventory kafka template success | +4,596 |
| payment `PaymentPrepareRequested` consume failed | +40 |
| payment outbox `PaymentFailed` | +1 |

## 2) p99 Supplement Run (60s, 20 VU)

- Purpose: capture explicit p99 value

| Metric | Value |
|---|---:|
| Iteration rate | 26.53/s |
| Request rate | 26.54/s |
| HTTP avg latency | 633 ms |
| HTTP p95 latency | 990 ms |
| HTTP p99 latency | 1,252 ms |
| HTTP max latency | 1,529 ms |

## 3) Interpretation

- Kafka/event pipeline for order->inventory->order loop is active:
  - inventory command consume and event publish deltas track order create attempts.
- Order status transition observed for successful requests:
  - success subset reached `PAYMENT_PENDING` (confirm excluded flow 기준).
- Failure concentration:
  - 4,586건이 409로 실패.
  - 샘플 에러는 `ORDER_409_001 (결제 준비 요청 가능한 주문 상태가 아닙니다.)`.
- Payment consumer health issue exists during test window:
  - `PaymentPrepareRequested` failed consume counter keeps increasing (+40).

## 4) Lock validation note

- 이번 실행에서 락 성공/실패를 직접 분해하는 전용 metric (`lock_acquire_success/fail`)은 없어 정밀 분리가 어렵습니다.
- 다만 inventory consume/publish는 요청 수와 동일 수준으로 처리되어 파이프라인 자체는 동작했습니다.
- 다음 검증 권장:
  - `wearhouse_inventory_lock_acquire_total{result=success|failed}` 추가
  - `reasonCode`(OUT_OF_STOCK vs HOT_SKU_LOCK_ACQUIRE_FAILED) 카운터 추가

