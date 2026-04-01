# Distributed Tracing (MSA) - Prometheus + Tempo + Grafana

## 핵심 정리
- Prometheus는 **메트릭 저장소**다. (trace 원본 저장 X)
- 분산 트레이스 원본(span)은 Tempo가 저장한다.
- OTel Collector가 OTLP trace를 받아 Tempo로 전달하고, spanmetrics를 만들어 Prometheus로 노출한다.
- Grafana는 Prometheus/Tempo 두 datasource를 함께 사용해 호출 흐름/지연 구간을 본다.

## 적용된 구성
- `docker-compose.yml`
  - `tempo` 추가
  - `otel-collector` 추가
  - 주요 서비스(`api-gateway`, `auth`, `user`, `product`, `cart`, `order`, `payment`, `inventory`)에 tracing env 추가
- `docker/monitoring/otel-collector/config.yml`
  - OTLP receiver + spanmetrics connector + prometheus exporter + tempo exporter
- `docker/monitoring/tempo/tempo.yml`
  - tempo 로컬 스토리지 설정
- `docker/monitoring/prometheus/prometheus.yml`
  - `otel-collector:9464` 스크랩 추가
- `docker/monitoring/grafana/provisioning/datasources/datasource.yml`
  - Tempo datasource 추가
- `docker/monitoring/grafana/dashboards/distributed-tracing-overview.json`
  - 서비스별 trace call rate/p95/error rate
- `config-repo/application.yml`
  - `management.tracing.*`, `management.otlp.tracing.endpoint` 추가

## 서비스 코드(의존성)
아래 모듈에 OTel tracing 의존성 추가:
- `api-gateway`, `auth`, `user`, `product`, `cart`, `order`, `payment`, `inventory`, `settlement`

추가된 의존성:
- `io.micrometer:micrometer-tracing-bridge-otel`
- `io.opentelemetry:opentelemetry-exporter-otlp`

## 실행
```bash
docker compose up -d --build
```

## 확인 포인트
1. Prometheus
- `http://localhost:9090`
- 쿼리 예시:
  - `traces_spanmetrics_calls_total`
  - `traces_spanmetrics_latency_bucket`

2. Grafana
- `http://localhost:3000`
- 기본 계정: `admin / admin` (env 변경 가능)
- 대시보드: `Distributed Tracing Overview`
- Explore에서 datasource를 `Tempo`로 선택하면 trace 검색 가능

### Kafka 이벤트 추적 확인 방법
전제:
- `KAFKA_TEMPLATE_OBSERVATION_ENABLED=true`
- `KAFKA_LISTENER_OBSERVATION_ENABLED=true`

확인 순서:
1. 주문/결제/재고 흐름 API를 1~2회 호출해 이벤트를 발생시킨다.
2. Grafana -> Explore -> datasource `Tempo` 선택
3. TraceQL 예시:
   - 전체 Kafka span: `{ span.kind = "producer" || span.kind = "consumer" }`
   - 주문 서비스 Kafka 발행: `{ resource.service.name = "order-service" && span.kind = "producer" }`
   - 재고 서비스 Kafka 소비: `{ resource.service.name = "inventory-service" && span.kind = "consumer" }`
4. 하나의 trace를 열어 `api-gateway -> order-service -> kafka producer -> inventory/payment consumer` 순으로 연결되는지 확인

Prometheus 쿼리(집계 지표):
- 서비스별 Kafka span 호출률:
  - `sum(rate(traces_spanmetrics_calls_total{messaging_system=\"kafka\"}[1m])) by (service_name, messaging_operation)`
- 토픽별 Kafka span 호출률:
  - `sum(rate(traces_spanmetrics_calls_total{messaging_system=\"kafka\"}[1m])) by (messaging_destination_name, messaging_operation)`

재고 예약 여부(주문 기준) 집계:
- 메트릭: `wearhouse_order_inventory_reserve_result_total{result=\"requested|reserved|failed\"}`
- 분당 예약 요청 수:
  - `sum(rate(wearhouse_order_inventory_reserve_result_total{result=\"requested\"}[1m]))`
- 분당 예약 성공 수:
  - `sum(rate(wearhouse_order_inventory_reserve_result_total{result=\"reserved\"}[1m]))`
- 분당 예약 실패 수:
  - `sum(rate(wearhouse_order_inventory_reserve_result_total{result=\"failed\"}[1m]))`
- 예약 성공률:
  - `sum(rate(wearhouse_order_inventory_reserve_result_total{result=\"reserved\"}[5m])) / clamp_min(sum(rate(wearhouse_order_inventory_reserve_result_total{result=\"requested\"}[5m])), 0.0001) * 100`
- 예약 미완료(대기) 추정:
  - `sum(increase(wearhouse_order_inventory_reserve_result_total{result=\"requested\"}[10m])) - sum(increase(wearhouse_order_inventory_reserve_result_total{result=~\"reserved|failed\"}[10m]))`

3. Tempo
- `http://localhost:3200`

## 주의
- 현재 `config-repo/application.yml` 기본값은 `TRACING_ENABLED=false`다.
- docker-compose에서는 서비스 env로 `TRACING_ENABLED=true`를 넘겨 tracing을 켠다.
- EKS 적용 시에는 OTel Collector/Tempo(혹은 AWS X-Ray 등) 준비 후 env를 켜는 방식으로 반영하면 된다.
