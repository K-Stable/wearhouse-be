# Guest Order Checkout Prepare Load Test Report

- Date: 2026-03-31 (KST)
- Environment: EKS `wearhouse-eks` (`ap-northeast-2`)
- Flow: `guest create order -> (checkoutUrl 단계 전)`
- Profile: `100 VU / 200 RPS / 3m` (`constant-arrival-rate`)
- Script: `scripts/loadtest/order-checkout-no-confirm.k6.js`

## 1) 테스트 전 재고 리필

`wearhouse_inventory.inventory_stock` 기준:

| sku_id | available_qty | reserved_qty | product_status |
|---:|---:|---:|---|
| 1 | 20,000 | 0 | RELEASED |
| 2 | 20,000 | 0 | RELEASED |
| 3 | 20,000 | 0 | RELEASED |

게스트 스모크 호출:
- `POST /order-service/api/v1/buyer/guest/orders` -> `200 OK`

## 2) 부하 테스트 결과 (재실행 기준)

원본: `docs/perf/raw/order-checkout-prepare-loadtest-guest-2026-03-31-rerun2.{log,json}`

| Metric | Value |
|---|---:|
| HTTP requests | 1,185 |
| Request rate | 5.71 req/s |
| Iterations | 1,185 |
| HTTP avg latency | 16,175.09 ms |
| HTTP p95 latency | 30,012.90 ms |
| HTTP p99 latency | 30,051.07 ms |
| HTTP max latency | 30,187.88 ms |
| Order create success | 605 |
| Order create failure | 580 |
| Error rate (`http_req_failed`) | 48.95% |
| Dropped iterations | 34,825 |
| Drop rate | 167.79 /s |

해석:
- 목표 부하(200 RPS)에 비해 실제 처리량이 크게 낮고, 타임아웃(30s) 구간이 다수 발생.
- 성공/실패가 혼재하며, 실패는 주로 요청 타임아웃으로 관측됨.

## 3) 이벤트/상태/Saga/Kafka/락 추적 (전후 Delta)

비교 구간:
- Before: 스모크 1건 직후 스냅샷
- After: 부하 테스트 종료 직후 스냅샷

### 3-1) Order 서비스

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| reserve requested | 1 | 562 | +561 |
| reserve reserved | 1 | 503 | +502 |
| reserve failed | 0 | 2 | +2 |
| saga `none -> waiting_inventory` | 1 | 562 | +561 |
| saga `waiting_inventory -> waiting_payment_prepare` | 1 | 503 | +502 |
| saga `waiting_inventory -> reserve_failed` | 0 | 2 | +2 |
| status `none -> pending_reserve` | 1 | 562 | +561 |
| status `pending_reserve -> reserved` | 1 | 503 | +502 |
| status `pending_reserve -> reserve_failed` | 0 | 2 | +2 |

### 3-2) Inventory 서비스 (락/예약)

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| lock acquire success | 1 | 503 | +502 |
| lock acquire timeout | 0 | 2 | +2 |
| lock batch success | 1 | 503 | +502 |
| lock hold seconds sum | 0.845 | 14.79 | +13.945 |
| reserve requested | 1 | 505 | +504 |
| reserve reserved | 1 | 503 | +502 |
| reserve failed (`inventory_409_003`) | 0 | 2 | +2 |

### 3-3) Payment 서비스 (Kafka consume)

| Metric | Before | After | Delta |
|---|---:|---:|---:|
| `PaymentPrepareRequested` consume failed | 4 | 16 | +12 |

## 4) 테스트 후 재고 상태

| sku_id | available_qty | reserved_qty | product_status |
|---:|---:|---:|---|
| 1 | 19,456 | 544 | RELEASED |
| 2 | 20,000 | 0 | RELEASED |
| 3 | 20,000 | 0 | RELEASED |

## 5) 운영 이슈 메모

동일 날짜 첫 실행(`docs/perf/raw/order-checkout-prepare-loadtest-guest-2026-03-31.{log,json}`) 중 EKS 노드 1대가 `NotReady`로 전환되어 파드 재스케줄이 발생했습니다.
- 증상: 다수 파드 `Terminating/Pending`
- 조치: nodegroup `ng-45cd8975`를 2대로 scale-out하여 복구

현재는 전체 배포가 다시 `1/1` 정상 상태입니다.
