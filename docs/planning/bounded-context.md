# 바운디드 컨텍스트와 소유권

## 문서 정보

- 담당자:
- 리뷰어:
- 최종 수정일:
- 상태: Draft | Approved

## 컨텍스트 맵

| 서비스 | 소유 데이터 | 조회 가능 데이터 | 수정 금지 대상 |
|---|---|---|---|
| auth-service | 자격증명, 토큰 | 사용자 요약 정보 | order, payment, inventory |
| user-service | 프로필, 배송지 | 주문 요약 뷰 | payment, settlement |
| product-service | 상품, 옵션, 가격, 노출 상태 | 재고 가용 스냅샷 | order, payment |
| inventory-service | 가용/예약/판매 재고 | 상품 옵션 메타데이터 | 주문 상태, 결제 상태 |
| order-service | 주문 집합체, 주문 라인, 주문 상태 | 결제/재고 이벤트 상태 | 결제 트랜잭션 원본 |
| payment-service | 결제 트랜잭션, 환불 기록 | 주문 의도 메타데이터 | 재고 수량 |
| settlement-service | 정산 원장, 지급 기록 | 결제 성공/환불 이벤트 | order/inventory 쓰기 |

## 외부 연동 컨텍스트

| 외부 시스템 | 연동 서비스 | 목적 | 장애 시 정책 |
|---|---|---|---|
| PG | payment-service | 결제 세션/승인/취소/환불 | 웹훅 우선 + 재조회 정합화 |
| ERP/WMS | inventory-service, settlement-service | 재고/정산 기준 연동 | 지연 큐 적재 + 대사 배치 |

## 동기/비동기 경계

| 경계 | 방식 | 원칙 |
|---|---|---|
| 주문 생성 시 재고/결제 핵심 검증 | 최소 동기 호출 | 빠른 실패 + 서킷브레이커 |
| 후속 반영(조회모델/정산/알림) | 비동기 이벤트 | Kafka + 재처리 가능 구조 |
| 대량 데이터 동기화 | 배치 API/이벤트 리플레이 | 커맨드 경로와 분리 |

## 업스트림 / 다운스트림

| 업스트림 | 다운스트림 | 계약 |
|---|---|---|
| order | inventory | 예약/해제/확정 커맨드 |
| order | payment | 결제 준비/취소/환불 커맨드 |
| payment | settlement | PaymentSucceeded/RefundSucceeded 이벤트 |
| inventory | order | StockReserved/StockReserveFailed/InventoryReleased 이벤트 |
| PG webhook | payment | 결제 승인/실패 결과 전달 |

## 소유권 규칙

- 집합체 단일 작성자 원칙:
- 서비스 간 수정 규칙:
- 서비스 간 DB 직접 접근 금지:
- API 또는 이벤트 경계만 사용:
- 읽기 모델은 별도 저장소/캐시로 분리 가능(CQRS):

## 적용 결정

- 이벤트 발행은 애플리케이션 Outbox 직접 구현 방식으로 적용한다.
- Outbox 저장은 `BEFORE_COMMIT`, 즉시 발행은 `AFTER_COMMIT`에서 수행한다.
- 즉시 발행 실패 건은 상태 기반 배치(10분 경과 기준)로 재발행한다.
- Outbox Relay 완전 분리 여부는 추후 ADR에서 결정한다.

## 결정 기록

| ADR ID | 결정 내용 | 날짜 | 담당자 |
|---|---|---|---|
| ADR- |  |  |  |
