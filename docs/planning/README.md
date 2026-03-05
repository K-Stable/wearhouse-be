# Wearhouse 설계 문서

이 폴더는 아키텍처/거래 정책 설계 스프린트를 위한 템플릿 모음입니다.

## 권장 작성 순서

1. `invariants.md`
2. `bounded-context.md`
3. `order-state-machine.md`
4. `event-contracts.md`
5. `idempotency-policy.md`
6. `inventory-concurrency.md`
7. `failure-recovery.md`
8. `settlement-policy.md`
9. `test-matrix.md`
10. `observability-slo.md`
11. `musinsa-reference-notes.md`
12. `traffic-mode-strategy.md`
13. `infra-runbook.md`

## 작성 규칙

- 구현 상세보다 정책/계약을 먼저 확정합니다.
- 모든 문서 섹션에 담당자, 완료 예정일, 승인 상태를 둡니다.
- 정책이 바뀌면 관련 문서를 같은 날 함께 업데이트합니다.

## 무신사 사례 반영 체크리스트

- 읽기/쓰기 분리(CQRS)로 주문 쓰기 경로 보호 여부
- 이벤트 기반 처리(EDA) + 보상 트랜잭션(Saga) 설계 여부
- 재고 다층 방어(캐시 원자연산 + 분산락 + DB 최종 검증) 적용 여부
- DLQ, 중복방지, 재처리 런북 등 운영 안전장치 정의 여부
- AIOps 기반 이상징후 탐지 + Feature Flag 킬스위치 운영 준비 여부
- ERP/WMS/외부시스템 연동 시 정합성 대사 및 장애 시 우회 경로 정의 여부
