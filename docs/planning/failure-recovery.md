# 장애 및 복구 정책

## 문서 정보

- 담당자:
- 리뷰어:
- 최종 수정일:
- 상태: Draft | Approved

## 운영 모드 연계

- 평시/피크/비상 전환 기준은 `traffic-mode-strategy.md`를 단일 기준으로 사용한다.

## 장애 분류

| 유형 | 예시 | 탐지 방식 |
|---|---|---|
| 일시적 장애 | 네트워크 타임아웃, 브로커 지연 | 재시도/메트릭 |
| 비즈니스 장애 | 품절, 잘못된 상태 전이 | 도메인 검증 |
| 외부 의존 장애 | PG 타임아웃, 웹훅 지연 | 정합화 배치 |
| 데이터 장애 | 중복 이벤트, 누락 이벤트 | inbox/outbox 감사 |

## 재시도 정책

| 컴포넌트 | 재시도 | 백오프 | 최대 횟수 |
|---|---|---|---|
| 동기 커맨드 | Y/N |  |  |
| 비동기 컨슈머 | Y | exponential |  |
| PG 웹훅 정합화 조회 | Y | fixed/exponential |  |

## 보상 매트릭스

| 실패 동작 | 보상 동작 | 담당 |
|---|---|---|
| 결제 승인 실패/타임아웃 | `InventoryReleaseRequested` 발행 후 예약 재고 해제 | order/inventory |
| 주문 확정 | 결제 정합화 후 롤백 | order/payment |
| 환불 이벤트 소비 | DLQ 리플레이 | settlement |
| 조회계 적재 실패 | 재시도 후 역순 재적재 | read-model worker |

## DLQ 및 리플레이

- DLQ 토픽:
- 리플레이 담당:
- 리플레이 안전 점검:
- 리플레이 런북 링크:

## Outbox 재발행 배치

| 항목 | 정책 |
|---|---|
| 대상 | `status != SEND_SUCCESS` |
| 시간 조건 | `created_at <= now - 10분` |
| 실행 주기 | 1분 |
| 락 | `FOR UPDATE SKIP LOCKED` |
| 성공 처리 | `SEND_SUCCESS` + `published_at` 기록 |
| 실패 처리 | `SEND_FAIL` + `retry_count` 증가 + `next_retry_at` 갱신 |
| 중단 처리 | 임계치 초과 시 `DEAD` 전환 + 알람 |

## 정합화 배치

| 배치 | 주기 | 비교 대상 | 조치 |
|---|---|---|---|
| order-payment |  | 주문 최종 상태 vs 결제 최종 상태 | 자동/수동 보정 |
| order-inventory |  | 주문 수량 vs sold/reserved | 보상 처리 |
| payment-settlement |  | 결제/환불 vs 원장 | 조정 분개 |
| inventory-cache |  | DB 재고 vs Redis 재고 | 캐시 재적재 |

## 폴백 정책

| 장애 대상 | 폴백 동작 |
|---|---|
| Redis | DB 직조회/직갱신 제한 모드 전환 |
| Elasticsearch/조회 저장소 | DB 조회 폴백 + 캐시 워밍 우선순위 조정 |
| Kafka 브로커 지연 | 중요 커맨드는 동기 경로 보호, 이벤트는 Outbox 누적 |

## 기능 플래그/킬스위치

- 고위험 기능(쿠폰, 추천, 대량프로모션)은 플래그 기반 점진 배포
- 장애 전파 시 기능별 킬스위치로 즉시 차단
- 플래그 변경 이력은 감사 로그로 보존

## 장애 대응

- 심각도 기준:
- 호출(Paging) 정책:
- RTO/RPO 목표:
- 이상 징후 자동 탐지 기준(AIOps):
