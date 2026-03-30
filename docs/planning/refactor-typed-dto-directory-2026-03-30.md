# Wearhouse 리팩터링 정리 (디렉토리 구조 + Typed DTO)

작성일: 2026-03-30

## 1) 이번 라운드 목표
- 서비스/계층 책임을 명확히 분리해서 유지보수 난이도 낮추기
- Kafka 이벤트 처리에서 `Map<String, Object>` 의존을 줄이고 타입 안정성 강화
- Inbox 처리 시그니처를 단순화 가능한 곳은 단순화

`OrderPaymentIntegrationWaiter`의 이벤트 기반 비동기 전환은 별도 테스트 계획 이후 진행 예정.

---

## 2) 디렉토리 구조 변화 (핵심)

### 공통 방향
- actor 기준 분리: `buyer`, `seller`, `internal`
- 흐름 기준 분리: `paymentintegration`, `saga`, `webhook`, `transaction`
- 기술/횡단관심 분리: `support/config`, `support/security`, `infra/*`
- 변환 책임 분리: `mapper`

### 현재 구조(요약)

```text
order
├─ buyer/{controller,dto,mapper,service}
├─ seller/{controller,dto,mapper,service}
├─ delivery/{controller,dto,mapper,service}
├─ paymentintegration/{client,dto,mapper,service}
├─ saga/service
├─ kafka/{consumer,dto,publisher}
├─ domain/{entity,event,model}
├─ infra/jpa
└─ support/{config,security}

inventory
├─ buyer/{controller,dto,mapper,service}
├─ seller/{controller,dto,service}
├─ internal/dto
├─ kafka/dto
├─ infra/{jpa,kafka,product,redis}
├─ domain/{entity,event,exception,model,repository,response}
└─ support/{config,security}

payment
├─ internal/{controller,dto,mapper,service}
├─ kafka/{consumer,dto,handler,publisher}
├─ stablepay/client
├─ transaction/service
├─ webhook/{controller,dto,security,service}
├─ domain/payment
├─ infra/{jpa,kafka,pay}
└─ support/{config,monitoring,security}

user
├─ buyer/{controller,service}
├─ seller/{controller,service}
├─ internal/{controller,mapper,service}
├─ domain/*
├─ infra/jpa
└─ support/{config,cookie,security}
```

---

## 3) Typed DTO 2차 마이그레이션

## 3-1. DomainEvent payload 타입 확장
- `OrderDomainEvent`, `InventoryDomainEvent`, `PaymentDomainEvent`
- payload를 `Map<String,Object>` 고정에서 `Object`로 변경
- Envelope는 유지 (`eventId`, `eventType`, `aggregateId`, `payload` 등)

효과:
- 발행 측에서 record DTO를 payload로 직접 사용 가능
- 필드 오타/누락을 컴파일 단계에서 더 일찍 검출

## 3-2. 발행 payload를 typed record로 전환
- `BuyerOrderCreateOrchestrationService`
  - `InventoryReserveRequestedPayload`, `ReservePayloadItem`
- `OrderSagaService`
  - `PaymentPrepareRequestedPayload`, `InventoryReleaseRequestedPayload`, `OrderConfirmedPayload`
- `BuyerOrderCancelOrchestrationService`
  - `InventoryReleaseRequestedPayload`
- `BuyerInventoryCommandService`
  - `StockReservedPayload`, `StockReserveFailedPayload`, `InventoryReleasedPayload`
- `PaymentEventPublishService`
  - `PaymentAuthorizedPayload`, `PaymentAuthorizedFromWebhookPayload`, `PaymentFailedPayload`

## 3-3. consumer/handler 체인 타입 고정 강화
- `PaymentCommandConsumer -> PaymentPrepareRequestedHandler -> PaymentInternalCommandService`
  - raw string/topic/key 전달을 제거하고 `eventId + PaymentPrepareRequestedEvent` 중심으로 정리
- `OrderSagaService`
  - 내부 디스패치를 `Object` 캐스팅 맵 방식에서 이벤트 타입 분기 방식으로 전환

## 3-4. Kafka envelope 공통 DTO 정리
- `KafkaMessageEnvelope.payload`를 `Object`로 변경
- 각 consumer에서 `objectMapper.convertValue(payload, EventRecord.class)`로 명시적 타입 변환

---

## 4) PaymentInboxRepository 시그니처 결정

결론: **order/inventory처럼 완전 슬림화는 보류**

이유:
- `payment_inbox_event`는 `event_type`, `topic`, `payload`, `fail_reason_code/message` 컬럼을 실제로 보관
- payment는 결제 장애 분석 시 inbox 데이터의 포렌식 가치가 큼
- `markFailed(reasonCode, reasonMessage)`는 현재도 저장 컬럼과 1:1 매칭

적용:
- payment 소비 체인은 typed로 정리하되, inbox 저장 메타데이터(`eventType/topic/payload`)는 유지
- 즉, "처리 API는 단순화" + "저장 데이터는 유지" 전략

---

## 5) 추가로 함께 반영된 구조 개선
- `OrderPaymentIntegrationService` 책임 분리
  - `Validator`, `Waiter`, `GatewayAdapter`, `Sleeper`
- `user` 내부 API
  - `@Value` 제거, `@ConfigurationProperties` + mapper 분리

---

## 6) 검증 결과

실행 커맨드:

```bash
./gradlew :common:compileJava :order:testClasses :inventory:testClasses :payment:testClasses :user:compileJava \
  :order:test --tests "*OrderSagaServiceFlowTest" \
  :payment:test --tests "*PaymentCommandServiceStablepayTest" \
  :inventory:test --tests "*InventoryCommandServiceConcurrencyTest" --no-daemon
```

결과:
- `BUILD SUCCESSFUL`

---

## 7) 포트폴리오 어필 포인트
- 이벤트 드리븐 아키텍처에서 **Map 기반 payload를 typed DTO로 단계적 전환**한 실무형 리팩터링 경험
- 단순 "폴더 이동"이 아니라 **consumer→handler→service→publisher 전체 체인**을 타입 안정성 기준으로 재설계
- 결제 도메인에서 inbox 포렌식 정보를 보존하는 등 **운영 관점(관측/장애분석) 트레이드오프를 명시적으로 의사결정**
- 대규모 리팩터링 이후에도 핵심 테스트를 유지한 **안전한 변경 전략(compile + focused test)**
