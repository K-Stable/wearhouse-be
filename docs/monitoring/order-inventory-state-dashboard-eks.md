# EKS 상태 추적 대시보드 구성 가이드

## 1) 이번에 적용한 것

- Prometheus/Grafana 스택 설치: `kube-prometheus-stack`
- 서비스 메트릭 수집 연결: `order-service`, `inventory-service`, `payment-service` (PodMonitor)
- 알림 규칙 추가: 주문 재고예약 실패율, 재고 락 실패 증가, 결제 consume 실패
- Grafana 대시보드 추가: `Order-Inventory State Tracking`

적용 파일:
- `k8s/monitoring/kube-prometheus-stack-values.yaml`
- `k8s/monitoring/wearhouse-podmonitor.yaml`
- `k8s/monitoring/wearhouse-prometheus-rule.yaml`
- `k8s/monitoring/kustomization.yaml`
- `k8s/monitoring/order-inventory-state-tracking.json`

## 2) 설치/반영 명령

```bash
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install wearhouse-monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring --create-namespace \
  -f k8s/monitoring/kube-prometheus-stack-values.yaml \
  --server-side=false

kubectl apply -k k8s/monitoring
```

## 3) 접속 방법

Grafana 비밀번호:

```bash
kubectl -n monitoring get secret wearhouse-monitoring-grafana \
  -o jsonpath='{.data.admin-password}' | base64 -d
```

로컬 접속:

```bash
kubectl -n monitoring port-forward svc/wearhouse-monitoring-grafana 3000:80
```

- URL: `http://127.0.0.1:3000`
- ID: `admin`
- PW: 위 secret 조회값

## 4) 대시보드에서 보는 핵심 항목

대시보드명: `Order-Inventory State Tracking`

- 주문 재고 예약 성공률(5m)
- 재고 락 획득 성공률(5m)
- 결제 Kafka consume 실패(5m)
- 락 홀드 평균 시간(5m)
- 주문 상태 전이 추이
- Saga 상태 전이 추이
- 재고 예약 실패 사유 분해
- 결제 prepare 대기 결과(해결/타임아웃)

## 5) 즉시 확인 쿼리

Prometheus 포트포워딩:

```bash
kubectl -n monitoring port-forward svc/wearhouse-monitoring-kube-prometheus 9090:9090
```

확인 예시:

```promql
up{namespace="wearhouse"}
wearhouse_order_inventory_reserve_result_total
wearhouse_payment_kafka_consume_total
wearhouse_inventory_lock_acquire_total
wearhouse_order_status_transition_total
```

## 6) 운영 메모

- 현재 EKS 노드 파드 수 한도(max pods)가 낮으면 모니터링 파드 스케줄이 지연될 수 있습니다.
- 장기적으로는 노드 그룹 desired 수를 늘리거나, 모니터링 전용 노드를 분리하는 것을 권장합니다.
- `wearhouse_order_status_transition_total`, `wearhouse_inventory_lock_acquire_total` 같은 신규 지표는 해당 코드가 반영된 서비스 이미지 롤아웃 이후부터 값이 쌓입니다.
