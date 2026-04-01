# EKS 모니터링 최소화 (트랜잭션 추적 + 주문/결제 성능 대시보드만)

## 현재 구성

Grafana 대시보드 2개만 유지:
- `Distributed Tracing Overview`
- `Order-Payment Transaction Overview`

수집 대상:
- Trace: `api-gateway`, `order-service`, `payment-service`, `inventory-service` -> `otel-collector` -> `tempo`
- Metric: `order-service`, `payment-service`, `inventory-service`, `otel-collector(spanmetrics)` -> Prometheus

## 적용 파일

- `k8s/monitoring/kube-prometheus-stack-values.yaml`
- `k8s/monitoring/wearhouse-podmonitor.yaml`
- `k8s/monitoring/wearhouse-tracing-stack.yaml`
- `k8s/monitoring/order-payment-transaction-overview.json`
- `k8s/monitoring/distributed-tracing-overview.json`
- `k8s/monitoring/tempo-datasource.yaml`
- `k8s/monitoring/kustomization.yaml`

## 확인 명령

```bash
# Grafana 접속
kubectl -n monitoring port-forward svc/wearhouse-monitoring-grafana 3000:80

# 대시보드 확인
# - Distributed Tracing Overview
# - Order-Payment Transaction Overview

# Prometheus 확인
kubectl -n monitoring port-forward svc/wearhouse-monitoring-kube-prometheus 9090:9090
```

## 주의

- `traces_spanmetrics_*` 지표는 실제 주문/결제 요청이 들어와야 값이 보입니다.
- 현재 트레이싱 활성화 환경변수는 다음 배포에 반영됨:
  - `TRACING_ENABLED=true`
  - `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318/v1/traces`
- 주문 1건 단위(Event/Kafka/Status/Saga) 추적은 별도 가이드 참고:
  - `docs/monitoring/order-flow-per-order-tracking.md`
