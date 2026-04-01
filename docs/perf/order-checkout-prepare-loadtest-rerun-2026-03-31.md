# Order Checkout Prepare Load Test Report (Rerun)

- Date: 2026-03-31 (KST)
- Environment: EKS `wearhouse-eks` (`ap-northeast-2`)
- Scope: `login -> create order -> checkoutUrl 반환` (confirm 제외)
- Profile: `100 VU / 200 RPS / 3m` (`constant-arrival-rate`)
- Script: `scripts/loadtest/order-checkout-no-confirm.k6.js`

## 1) 재실행 결과

| Metric | Value |
|---|---:|
| HTTP requests | 1,407 |
| Request rate | 7.39 req/s |
| Iterations | 1,406 |
| Iteration rate | 7.39 iter/s |
| Avg latency | 13,145.77 ms |
| p95 latency | 18,918.95 ms |
| p99 latency | 22,533.08 ms |
| HTTP failed rate | 0.00% |
| Order create success | 1,406 |
| Order create failure | 0 |
| Drop (`dropped_iterations`) | 34,595 |
| Drop rate | 181.75 /s |

Threshold 결과:
- `http_req_duration p(95)<5000`: 실패
- `order_create_latency_ms p(99)<8000`: 실패
- `http_req_failed rate<0.99`: 통과
- `order_create_error_rate rate<0.99`: 통과

## 2) 이전 실행 대비 비교 (2026-03-31 1차 실행)

기준 문서: `docs/perf/order-checkout-prepare-loadtest-2026-03-31.md`

| Metric | 이전(1차) | 이번(재실행) | 변화 |
|---|---:|---:|---:|
| HTTP requests | 2,965 | 1,407 | -1,558 (-52.5%) |
| Request rate (req/s) | 16.18 | 7.39 | -8.79 (-54.3%) |
| Avg latency (ms) | 6,094 | 13,145.77 | +7,051.77 (+115.7%) |
| p95 latency (ms) | 8,600 | 18,918.95 | +10,318.95 (+120.0%) |
| p99 latency (ms) | 9,139 | 22,533.08 | +13,394.08 (+146.6%) |
| HTTP failed rate | 99.97% | 0.00% | -99.97%p |
| Order create success | 0 | 1,406 | +1,406 |
| Drop count | 33,037 | 34,595 | +1,558 (+4.7%) |

## 3) 이벤트/재고 예약 카운터 스냅샷

테스트 직후 서비스 내부 prometheus 노출값:

### order-service
- `wearhouse_order_inventory_reserve_result_total{result="requested"} = 1408`
- `wearhouse_order_inventory_reserve_result_total{result="reserved"} = 1406`
- `wearhouse_order_inventory_reserve_result_total{result="failed"} = 2`

### inventory-service
- `wearhouse_inventory_reserve_result_total{result="requested", reason_code="none"} = 1407`
- `wearhouse_inventory_reserve_result_total{result="reserved", reason_code="none"} = 1406`
- `wearhouse_inventory_reserve_result_total{result="failed", reason_code="inventory_409_001"} = 1`

## 4) 재고 락 메트릭 (이번 요청 반영)

테스트 직후 `inventory-service /actuator/prometheus` 스냅샷:

- `wearhouse_inventory_lock_acquire_total{result="success"} = 1407`
- `wearhouse_inventory_lock_batch_total{result="success"} = 1406`
- `wearhouse_inventory_lock_batch_total{result="failed"} = 1`
- `wearhouse_inventory_lock_hold_seconds_sum{result="success"} = 32.615`
- `wearhouse_inventory_lock_hold_seconds_max{result="success"} = 0.038`

해석:
- 락 획득 자체는 대부분 성공했고(`success`만 집계), `timeout/interrupted/error` 카운터는 관측되지 않음.
- 예약 실패 1건은 `reason_code="inventory_409_001"`(재고 부족)이며, 락 획득 실패로 보이지 않음.

## 5) 해석

- 성공률은 크게 개선됨:
  - 이전 실행은 거의 전량 실패였지만, 이번 실행은 본 부하 요청 기준 실패 0건으로 수렴.
- 반면 지연/처리량은 악화:
  - 성공 경로(재고 예약 + 결제 준비)를 실제로 많이 타면서 요청당 처리 시간이 크게 증가.
  - `100 VU` 제한에서 `200 RPS` 목표를 유지하지 못해 `dropped_iterations`가 계속 크게 발생.

## 6) 원본 아티팩트

- Raw log: `docs/perf/raw/order-checkout-prepare-loadtest-2026-03-31-rerun.log`
- Summary JSON: `docs/perf/raw/order-checkout-prepare-loadtest-2026-03-31-rerun.json`
