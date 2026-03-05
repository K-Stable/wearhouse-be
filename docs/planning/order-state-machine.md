# 주문 상태머신과 Saga

## 문서 정보

- 담당자:
- 리뷰어:
- 최종 수정일:
- 상태: Draft | Approved

## 주문 상태

- `PENDING`
- `STOCK_RESERVED`
- `PAYMENT_AUTHORIZED`
- `CONFIRMED`
- `PENDING_CONFIRMATION`
- `CANCEL_REQUESTED`
- `REFUND_PENDING`
- `CANCELLED`
- `FAILED`

## 허용 상태 전이

| 현재 상태 | 이벤트/조건 | 다음 상태 | 소유 서비스 |
|---|---|---|---|
| PENDING | 재고 예약 성공 | STOCK_RESERVED | order |
| PENDING | 재고 예약 실패 | FAILED | order |
| STOCK_RESERVED | 결제 승인 성공 | PAYMENT_AUTHORIZED | order |
| STOCK_RESERVED | 결제 승인 실패 | FAILED | order |
| STOCK_RESERVED | 결제 타임아웃/결과 미확정 | PENDING_CONFIRMATION | order |
| PENDING_CONFIRMATION | PG 재조회/웹훅으로 승인 확정 | PAYMENT_AUTHORIZED | order |
| PENDING_CONFIRMATION | PG 재조회/웹훅으로 실패 확정 | FAILED | order |
| PAYMENT_AUTHORIZED | 주문 확정 | CONFIRMED | order |
| CONFIRMED | 구매자/판매자 취소 요청 수락 | CANCEL_REQUESTED | order |
| CANCEL_REQUESTED | 환불 시작 | REFUND_PENDING | order |
| REFUND_PENDING | 환불 성공 + 재고 조정 완료 | CANCELLED | order |

## 금지 상태 전이

| 현재 상태 | 다음 상태 | 사유 |
|---|---|---|
| CANCELLED | CONFIRMED | 종료 상태 |
| FAILED | CONFIRMED | 결제/재고 유효성 불충족 |
| PENDING_CONFIRMATION | CONFIRMED | 결제 확정 전 선확정 금지 |

## Saga 단계 (구매)

1. 주문 생성 (`PENDING`)
2. 최소 동기 검증(재고 예약 + 결제 승인 시도)
3. 미확정 결제는 `PENDING_CONFIRMATION`으로 전환
4. 결제 확정 후 주문 확정 및 재고 판매 확정
5. 정산/조회모델/알림 반영은 이벤트 비동기 처리

## 보상 단계

| 실패 지점 | 보상 동작 |
|---|---|
| 재고 예약 실패 | 주문 실패 처리 |
| 결제 승인 실패 | 예약 재고 해제 |
| 주문 확정 실패 | 결제 상태 재조정 후 정책 기반 롤백 |
| 결제 장기 미확정 | 타임아웃 정책에 따라 자동 취소 + 재고 해제 |

## 타임아웃 정책

| 단계 | 타임아웃 | 동작 |
|---|---|---|
| 재고 예약 | 3-5초 | 재시도 또는 빠른 실패 |
| 결제 승인 | 10-30초 | 확인 대기 상태 전환 |
| 확인 대기 | 정책 윈도우 | PG 조회/웹훅으로 정합화 |

## 운영 가드레일

- 결제/재고 동기 경로에 서킷브레이커를 적용한다.
- 동기 검증 실패 시 비동기 보상으로 넘기지 않고 즉시 실패 처리한다.
- 이벤트 소비 지연 시 조회계(마이페이지/판매자 대시보드)만 지연되고 거래 커맨드는 보호되어야 한다.

## 시퀀스 다이어그램

- 다이어그램 위치:
- 최종 검토일:
