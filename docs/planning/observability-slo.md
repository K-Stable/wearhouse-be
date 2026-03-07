# 관측성 및 SLO

## 문서 정보

- 담당자: 주문/재고/결제 백엔드
- 리뷰어:
- 최종 수정일: 2026-03-07
- 상태: Draft

## 운영 모드 연계

- 평시/피크 모드별 SLO 임계치는 `traffic-mode-strategy.md`의 전환 기준과 함께 관리한다.

## 골든 시그널

| 도메인 | 지연시간 | 오류율 | 트래픽 | 포화도 |
|---|---|---|---|---|
| order | `wearhouse_order_kafka_consume_total`(result=success) 처리량 기준 p95 추적 | `wearhouse_order_kafka_consume_total`(result=failed) 비율 | `wearhouse_order_outbox_publish_attempt_total` | `wearhouse_order_outbox_event_count{status="send_fail|dead"}` |
| payment | 컨슈머 처리 지연(`wearhouse_payment_kafka_consume_total`) | `wearhouse_payment_kafka_consume_total`(result=failed) + `wearhouse_payment_event_publish_failure_total` | `wearhouse_payment_event_publish_attempt_total` | `wearhouse_payment_decision_total{result="pending_timeout"}` |
| inventory | 컨슈머 처리 지연(`wearhouse_inventory_kafka_consume_total`) | `wearhouse_inventory_kafka_consume_total`(result=failed) + `wearhouse_inventory_event_publish_failure_total` | `wearhouse_inventory_event_publish_attempt_total` | `wearhouse_inventory_concurrency_guard_total{result="acquire_fail|conflict"}` |
| settlement |  |  |  |  |

## SLO

| SLO ID | 목표 | 타깃 | 기간 |
|---|---|---|---|
| SLO-001 | 중복 없는 결제 승인 성공률 | 99.9% 이상 | 30일 |
| SLO-002 | 품절 이상 판매 방지 성공률 | 99.99% 이상 | 30일 |
| SLO-003 | 취소 정합성 수렴률 | 99.9% 이상(5분 내) | 30일 |
| SLO-004 | 이벤트 처리 신선도 | 핵심 토픽 lag p95 < 10초 | 30일 |
| SLO-005 | 조회계 지연 허용 범위 내 수렴률 |  |  |
| SLO-006 | 품절취소율 | 0.3% 이하 | 30일 |

## 알람

| 알람 | 조건 | 심각도 | 온콜 |
|---|---|---|---|
| 주문 컨슈머 실패율 급증 | `sum(rate(wearhouse_order_kafka_consume_total{result="failed"}[5m])) / sum(rate(wearhouse_order_kafka_consume_total[5m])) > 0.05` (10분 지속) | high | order |
| 재고 컨슈머 실패율 급증 | `sum(rate(wearhouse_inventory_kafka_consume_total{result="failed"}[5m])) / sum(rate(wearhouse_inventory_kafka_consume_total[5m])) > 0.05` (10분 지속) | high | inventory |
| 결제 컨슈머 실패율 급증 | `sum(rate(wearhouse_payment_kafka_consume_total{result="failed"}[5m])) / sum(rate(wearhouse_payment_kafka_consume_total[5m])) > 0.05` (10분 지속) | high | payment |
| 이벤트 발행 실패 급증(재고/결제) | `sum(rate(wearhouse_inventory_event_publish_failure_total[5m])) > 1 or sum(rate(wearhouse_payment_event_publish_failure_total[5m])) > 1` (5분 지속) | critical | platform |
| outbox dead 누적 | `wearhouse_order_outbox_event_count{status="dead"} > 0` (즉시) | critical | order |
| 락 경합 급증 | `sum(rate(wearhouse_inventory_concurrency_guard_total{control="hot_sku_lock",result="acquire_fail"}[5m])) > 5` | high | inventory |
| 낙관락 충돌 급증 | `sum(rate(wearhouse_inventory_concurrency_guard_total{control="optimistic_lock",result="conflict"}[5m])) > 10` | high | inventory |
| 결제 타임아웃 증가 | `sum(rate(wearhouse_payment_timeout_failed_total[15m])) > 0.2` | high | payment |

## 트레이스/로그 상관관계

- 모든 로그 필수 필드:
- 트레이스 전파 표준:
- 샘플링 전략:
- 이벤트 상관관계 분석 기준(주문ID/결제ID/재고예약ID):

## 대시보드

| 대시보드 | 담당자 | 링크 |
|---|---|---|
| order-health | order | 주문 outbox/consumer 실패율, dead 건수 |
| payment-integrity | payment | 결제 승인/실패/타임아웃 추세 |
| inventory-integrity | inventory | 락 실패율/낙관락 충돌/예약 만료 해제량 |
| settlement-integrity |  |  |

## AIOps 적용 항목

| 항목 | 목적 |
|---|---|
| 동적 임계치 기반 이상 감지 | 트래픽 급변 구간 오탐/미탐 최소화 |
| 이벤트 상관 분석 | 원인 서비스 빠른 식별 |
| 예측 알림 | 리소스/지연 악화 사전 대응 |

## 에러 버짓 정책

- 버짓 정의:
- 소진율(Burn Rate) 정책:
- 릴리스 게이트 반영:
