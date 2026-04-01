# Order Checkout Prepare Load Test (Guest, 20 Options)

- Date: 2026-03-31 (KST)
- Target: `https://api.wear-house.shop/order-service/api/v1/buyer/guest/orders`
- Scenario: checkout URL 전 단계(주문 생성 -> 재고 예약 이벤트)
- Tool: `k6` (`scripts/loadtest/order-checkout-no-confirm.k6.js`)

## 1) 데이터 준비

- `product_id=1` 옵션을 총 20개로 확장
  - 기존 9개 + 신규 11개
  - 옵션 ID 목록: `1,2,3,4,5,6,7,8,9,240,241,242,243,244,245,246,247,248,249,250`
- `wearhouse_inventory.inventory_stock` 재고 보충
  - 각 옵션 `available_qty=5000`, `reserved_qty=0`, `product_status=RELEASED`

## 2) 부하 조건

- `RATE=200`
- `VUS=100`
- `DURATION=3m`
- `ORDER_MODE=guest`
- `OPTION_IDS=1,2,3,4,5,6,7,8,9,240,241,242,243,244,245,246,247,248,249,250`

## 3) k6 결과

- `iterations`: `12169`
- `http_reqs`: `12169` (`67.29 req/s`)
- `http_req_failed`: `0.00%`
- `order_create_error_rate`: `0.00%`
- `http_req_duration`: `avg 1.46s`, `p95 2.15s`, `p99 2.45s`, `max 2.78s`
- `dropped_iterations`: `23832` (`131.78/s`)

## 4) 메트릭 증분 (Prometheus)

테스트 시작 전:
- lock_success=`568`
- reserve_requested=`568`
- reserve_reserved=`19`
- reserve_failed=`549`
- status_pending_reserve=`568`
- status_reserved=`19`
- status_reserve_failed=`549`

테스트 직후 증분:
- `wearhouse_inventory_lock_acquire_total{result="success"}`: `+3131`
- `wearhouse_inventory_lock_acquire_total{result="failed"}`: `+0`
- `wearhouse_order_inventory_reserve_result_total{result="requested"}`: `+12169`
- `wearhouse_order_inventory_reserve_result_total{result="reserved"}`: `+2345`
- `wearhouse_order_inventory_reserve_result_total{result="failed"}`: `+0`
- `wearhouse_order_status_transition_total{to_status="pending_reserve"}`: `+12169`
- `wearhouse_order_status_transition_total{to_status="reserved"}`: `+2345`
- `wearhouse_order_status_transition_total{to_status="reserve_failed"}`: `+0`

테스트 종료 후 추가 관찰:
- `reserve_requested=12737`
- `reserve_reserved=5336`
- `reserve_failed=549`

## 5) 해석

- 옵션 20개 분산 후에도 주문 API 자체는 `200` 응답으로 안정 동작.
- 락 실패 메트릭은 관측되지 않음(`lock_failed=0`), watchdog 적용 상태에서 락 획득 경로는 정상.
- 다만 `requested` 대비 `reserved` 반영이 지연되어, 비동기 소비/처리 구간의 백로그가 남아 있음.
- `200 RPS` 목표 대비 실제 처리량은 `~67 RPS`이고 drop이 큰 편이므로, 다음 단계는 `maxVUs` 상향/지연 최적화가 필요.
