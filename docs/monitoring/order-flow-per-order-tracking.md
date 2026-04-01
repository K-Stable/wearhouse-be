# 주문 1건 단위 추적 가이드 (Event/Kafka/Status/Saga)

## 목적

주문 1건(`orderNo`) 기준으로 아래를 한 번에 매칭해서 본다.

- 주문 상태 전이(`order_status_history`)
- 사가 상태 전이(`order_saga_history`)
- Kafka 발행 이벤트(`order_outbox_event`)
- Kafka 소비 이벤트(`order_inbox_event`)

## API

- `GET /api/v1/internal/orders/{orderNo}/tracking`

예시:

```bash
curl -s "https://api.wear-house.shop/order-service/api/v1/internal/orders/O202603310001/tracking" | jq
```

## 응답 핵심 필드

- `orderId`, `orderNo`
- `currentOrderStatus`
- `currentSagaState`
- `statusTransitions[]`
  - `fromStatus -> toStatus`
  - `eventId`
  - `reasonCode`
  - `changedAt`
- `sagaTransitions[]`
  - `fromState -> toState`
  - `eventId`
  - `reasonCode`
  - `changedAt`
- `kafkaPublishedEvents[]`
  - `eventId`
  - `eventType`
  - `topic`
  - `partitionKey`
  - `status(READY/SUCCESS/FAIL)`
  - `sentAt/failMessage`
- `kafkaConsumedEvents[]`
  - `eventId`
  - `eventType`
  - `topic`
  - `partitionKey`
  - `status(RECEIVED/PROCESSED/FAILED)`
  - `failReasonCode/failReasonMessage`
- `timeline[]`
  - 위 데이터를 시간순으로 합쳐서 반환

## Grafana와 같이 보는 방법

1. Grafana 대시보드에서 전체 처리량/지연/p95/p99/에러율 확인  
   - `Order-Payment Transaction Overview`
   - `Distributed Tracing Overview`
2. 이상 징후가 보이면 해당 주문번호(`orderNo`)로 위 tracking API 조회
3. `timeline`에서 어느 단계에서 멈췄는지 확인
   - `KAFKA_OUTBOX`에서 `FAIL`이면 발행 실패
   - `KAFKA_INBOX`에서 `FAILED`면 소비 처리 실패
   - `ORDER_STATUS`, `ORDER_SAGA` 전이 누락 여부 확인

## 참고

- 건별 추적은 고카디널리티 메트릭(주문번호 라벨)을 피하기 위해 API 기반으로 제공한다.
- 대시보드는 집계 관측(throughput/latency/error), tracking API는 건별 RCA(원인 분석) 용도로 분리한다.
