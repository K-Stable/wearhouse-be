# 멱등성 정책

## 문서 정보

- 담당자:
- 리뷰어:
- 최종 수정일:
- 상태: Draft | Approved

## API 적용 범위

| API | 멱등키 필수 여부 | 사유 |
|---|---|---|
| POST /orders | Y | 중복 주문 제출 방지 |
| POST /payments/authorize | Y | 이중 결제 방지 |
| POST /payments/refund | Y | 중복 환불 방지 |
| POST /orders/{id}/cancel | Y | 중복 취소 방지 |

## 키 규칙

- 헤더 이름: `X-Idempotency-Key`
- 키 포맷:
- 키 최대 길이:
- 키 TTL:
- 키 적용 범위(사용자/판매자/전역):

## 요청 검증 규칙

| 케이스 | 동작 |
|---|---|
| 최초 키 + 유효 요청 | 처리 후 응답 스냅샷 저장 |
| 동일 키 + 동일 요청 해시 | 저장된 응답 반환 |
| 동일 키 + 상이 요청 해시 | `409 CONFLICT` 반환 |
| 만료된 키 | 신규 요청으로 처리 |

## 저장 모델

| 필드 | 설명 |
|---|---|
| service | 소유 서비스 |
| idempotencyKey | 고유 키 |
| requestHash | 정규화 요청 해시 |
| statusCode | 원본 응답 코드 |
| responseBody | 원본 응답 본문 |
| expiresAt | TTL 만료 시각 |

## 비동기 멱등 처리

| 항목 | 정책 |
|---|---|
| 소비자 중복 처리 | `eventId` 기준 Inbox unique |
| 단기 중복 완화 | Redis `SETNX` + TTL |
| 프로듀서 유실 방지 | Outbox 저장 후 발행 |
| 리플레이 안전성 | 멱등 소비자만 DLQ 재처리 허용 |

## 운영 정책

- 정리 배치 주기:
- 핫키 모니터링:
- 오남용/스로틀링 정책:
