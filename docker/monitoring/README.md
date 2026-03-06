# Monitoring Stack

이 폴더는 Kafka 클러스터 모니터링을 위한 설정 파일을 포함한다.

구성:

- `kafka-exporter`: Kafka 메트릭 수집
- `prometheus`: 메트릭 수집/저장 및 알람 룰 평가
- `alertmanager`: 알람 라우팅
- `grafana`: 대시보드 시각화

기본 동작:

- Prometheus가 `kafka-exporter:9308/metrics`를 스크랩한다.
- Prometheus가 `order-service:8104/actuator/prometheus`를 스크랩한다.
- `docker/monitoring/prometheus/alert.rules.yml` 룰을 기준으로 알람을 발생시킨다.
- Alertmanager가 기본 webhook(`http://host.docker.internal:18080/alerts`)으로 알람을 전송한다.
- Grafana에 Kafka/Order 대시보드가 자동 프로비저닝된다.

주의:

- 실제 알람 채널(Slack, Discord, PagerDuty 등)은 `docker/monitoring/alertmanager/alertmanager.yml`에서 receiver를 변경해 사용한다.

Order Kafka 흐름 확인:

1. Order API 호출로 Outbox 이벤트 생성
2. `OrderOutboxRecordListener(BEFORE_COMMIT)`에서 Outbox 저장
3. `OrderOutboxPublishListener(AFTER_COMMIT)`에서 Kafka 발행 시도
4. 실패 이벤트는 `OrderOutboxRepublishScheduler`가 재발행
5. Prometheus/Grafana에서 아래 메트릭 확인

주요 메트릭:

- `wearhouse_order_outbox_record_total`
- `wearhouse_order_outbox_publish_attempt_total`
- `wearhouse_order_outbox_publish_success_total`
- `wearhouse_order_outbox_publish_failure_total`
- `wearhouse_order_outbox_event_count` (status gauge)
- `wearhouse_order_kafka_consume_total`

테스트 예시:

```bash
curl -X POST "http://localhost:8104/api/v1/orders" \
  -H "Content-Type: application/json" \
  -d '{
    "buyerId": 1001,
    "paymentMethod": "CARD",
    "recipientName": "테스터",
    "recipientPhone": "01012345678",
    "zipCode": "06236",
    "address1": "서울 강남구 테스트로 1",
    "address2": "101호",
    "deliveryRequest": "문 앞에 놓아주세요",
    "shippingFee": 3000,
    "discountAmount": 0,
    "pointUsedAmount": 0,
    "items": [
      {
        "productId": 20001,
        "optionId": 30001,
        "sellerId": 40001,
        "productName": "테스트 상품",
        "optionName": "블랙/L",
        "unitPrice": 15900,
        "quantity": 1
      }
    ]
  }'
```
