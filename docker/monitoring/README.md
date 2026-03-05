# Monitoring Stack

이 폴더는 Kafka 클러스터 모니터링을 위한 설정 파일을 포함한다.

구성:

- `kafka-exporter`: Kafka 메트릭 수집
- `prometheus`: 메트릭 수집/저장 및 알람 룰 평가
- `alertmanager`: 알람 라우팅
- `grafana`: 대시보드 시각화

기본 동작:

- Prometheus가 `kafka-exporter:9308/metrics`를 스크랩한다.
- `docker/monitoring/prometheus/alert.rules.yml` 룰을 기준으로 알람을 발생시킨다.
- Alertmanager가 기본 webhook(`http://host.docker.internal:18080/alerts`)으로 알람을 전송한다.
- Grafana에 Kafka 대시보드가 자동 프로비저닝된다.

주의:

- 실제 알람 채널(Slack, Discord, PagerDuty 등)은 `docker/monitoring/alertmanager/alertmanager.yml`에서 receiver를 변경해 사용한다.
