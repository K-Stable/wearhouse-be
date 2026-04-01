# Order Checkout Prepare Load Test Report

- Date: 2026-03-31 (KST)
- Environment: EKS `wearhouse-eks` (`ap-northeast-2`)
- Scope: `login -> create order -> checkoutUrl 반환 단계` (confirm 제외)
- Tool: `k6` (`constant-arrival-rate`)
- Requested profile: `100 VU / 200 RPS / 3m`

## 1) 실행 조건

- Script: `scripts/loadtest/order-checkout-no-confirm.k6.js`
- Target API:
  - `POST /auth-service/api/v1/auth/buyers/login` (setup 1회)
  - `POST /order-service/api/v1/buyer/orders` (본 부하)
- Request payload: `paymentMethod=STABLE`, `productId=1`, `optionId=1`

## 2) k6 결과

| Metric | Value |
|---|---:|
| HTTP requests | 2,965 |
| Request rate | 16.18 req/s |
| Iterations | 2,964 |
| Iteration rate | 16.17 iter/s |
| Avg latency | 6,094 ms |
| p95 latency | 8,600 ms |
| p99 latency | 9,139 ms |
| Max latency | 10,253 ms |
| HTTP failed rate | 99.97% |
| Order create success | 0 |
| Order create failure | 2,964 |
| Drop (`dropped_iterations`) | 33,037 |
| Drop rate | 180.24 /s |

참고:
- 실행 중 경고: `Insufficient VUs, reached 100 active VUs`
- 즉, `200 RPS` 목표를 `100 VU` 제한 내에서 유지하지 못해 drop이 크게 발생함

## 3) 이벤트/Kafka/재고락 추적 (Prometheus delta)

수집 기간: 테스트 직전/직후 스냅샷 차이

| Metric | Delta |
|---|---:|
| `wearhouse_order_inventory_reserve_result_total{result="requested"}` | +2,965 |
| `wearhouse_order_inventory_reserve_result_total{result="reserved"}` | +1 |
| `wearhouse_order_inventory_reserve_result_total{result="failed"}` | +2,964 |
| `wearhouse_payment_kafka_consume_total{event_type="PaymentPrepareRequested",result="failed"}` | +4 |
| `wearhouse_payment_kafka_consume_total{event_type="PaymentPrepareRequested",result="success"}` | +0 |

## 4) 관측 한계

- 현재 Prometheus에 노출된 `wearhouse_*` 메트릭이 제한적이라 아래 항목은 EKS에서 이번 실행 기준 집계 불가:
  - `wearhouse_inventory_reserve_result_total`
  - `wearhouse_inventory_lock_acquire_total`
  - `wearhouse_order_status_transition_total`
  - `wearhouse_order_saga_transition_total`
- 내부 추적 API `GET /api/v1/internal/orders/{orderNo}/tracking`는 gateway 경유 및 직접 서비스 조회 모두 EKS에서 `404/403` 확인됨(배포 이미지/라우팅 불일치 가능성).

## 5) 사전 검증 (단건)

- 부하 테스트 시작 전 단건 주문은 checkoutUrl 정상 반환 확인:
  - `orderNo`: `ORD20260330195311GXwtBv`
  - `checkoutSessionId`: `cs_1774900392074_475f318978bab795`

