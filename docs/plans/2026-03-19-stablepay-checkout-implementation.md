# StablePay Checkout Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Keep `POST /api/v1/orders` as the single buyer checkout entrypoint while adding `STABLEPAY` (Pay session + webhook finalize) without breaking existing order saga.

**Architecture:** Order creation and inventory reservation flow stay as-is. For `STABLEPAY`, payment-service becomes the source of truth for stablecoin payment state and webhook handling, then publishes existing `PaymentAuthorized` / `PaymentFailed` events back into the saga. Order-service orchestrates session preparation and returns session data in `OrderCreateResponse`.

**Tech Stack:** Spring Boot, Spring Security, Spring Kafka, Spring Data JPA, MySQL(Flyway), Feign client, JUnit5/Mockito

---

### Task 1: Introduce `PaymentMethod` Enum In Order Domain

**Files:**
- Create: `order/src/main/java/com/wearhouse/order/domain/model/PaymentMethod.java`
- Modify: `order/src/main/java/com/wearhouse/order/domain/dto/request/OrderCreateRequest.java`
- Modify: `order/src/main/java/com/wearhouse/order/domain/entity/OrderInfo.java`
- Modify: `order/src/main/java/com/wearhouse/order/domain/service/command/OrderCommandService.java`
- Test: `order/src/test/java/com/wearhouse/order/domain/service/command/OrderCommandServicePaymentMethodTest.java`

**Step 1: Write the failing test**

```java
@Test
void stablepay_method_is_accepted_and_propagated_to_order_info() {
    OrderCreateRequest request = OrderCreateRequest.builder()
            .buyerId(1L)
            .paymentMethod(PaymentMethod.STABLEPAY)
            .recipientName("tester")
            .recipientPhone("01012345678")
            .zipCode("12345")
            .address1("Seoul")
            .items(List.of(item()))
            .build();
    OrderCreateResponse response = orderCommandService.createOrder(request);
    assertThat(response.status()).isEqualTo("PENDING_RESERVE");
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :order:test --tests "*OrderCommandServicePaymentMethodTest"`  
Expected: FAIL because `PaymentMethod` enum does not exist yet.

**Step 3: Write minimal implementation**

```java
public enum PaymentMethod {
    CARD,
    STABLEPAY
}
```

```java
public record OrderCreateRequest(
        Long buyerId,
        @NotNull PaymentMethod paymentMethod,
        ...
) {}
```

```java
@Enumerated(EnumType.STRING)
@Column(name = "payment_method", length = 30)
private PaymentMethod paymentMethod;
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :order:test --tests "*OrderCommandServicePaymentMethodTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add order/src/main/java/com/wearhouse/order/domain/model/PaymentMethod.java \
  order/src/main/java/com/wearhouse/order/domain/dto/request/OrderCreateRequest.java \
  order/src/main/java/com/wearhouse/order/domain/entity/OrderInfo.java \
  order/src/main/java/com/wearhouse/order/domain/service/command/OrderCommandService.java \
  order/src/test/java/com/wearhouse/order/domain/service/command/OrderCommandServicePaymentMethodTest.java
git commit -m "feat(order): add payment method enum with stablepay"
```

### Task 2: Extend Payment Schema For StablePay + Webhook Dedupe

**Files:**
- Create: `payment/src/main/resources/db/migration/V5__add_stablepay_columns_and_webhook_event.sql`
- Modify: `payment/src/main/java/com/wearhouse/payment/domain/payment/entity/PaymentTransactionEntity.java`
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/entity/PaymentWebhookEventEntity.java`
- Create: `payment/src/main/java/com/wearhouse/payment/infra/jpa/repository/PaymentWebhookEventJpaRepository.java`
- Create: `payment/src/main/java/com/wearhouse/payment/infra/jpa/repository/PaymentWebhookEventRepository.java`
- Test: `payment/src/test/java/com/wearhouse/payment/domain/payment/entity/PaymentTransactionEntityTest.java`

**Step 1: Write the failing test**

```java
@Test
void transaction_should_store_stablepay_identifiers() {
    PaymentTransactionEntity entity = PaymentTransactionEntity.pending(...);
    entity.bindStablepaySession("pay_1", "ps_1", "merchant_1", "0xpayer", "0xtoken");
    assertThat(entity.getPaymentKey()).isEqualTo("pay_1");
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :payment:test --tests "*PaymentTransactionEntityTest"`  
Expected: FAIL because stablepay fields/method are missing.

**Step 3: Write minimal implementation**

```sql
ALTER TABLE payment_transaction
    ADD COLUMN payment_key VARCHAR(80) NULL,
    ADD COLUMN payment_session_id VARCHAR(80) NULL,
    ADD COLUMN merchant_key VARCHAR(120) NULL,
    ADD COLUMN payer_address VARCHAR(100) NULL,
    ADD COLUMN token_address VARCHAR(100) NULL,
    ADD COLUMN command_id VARCHAR(80) NULL,
    ADD COLUMN command_status VARCHAR(40) NULL,
    ADD COLUMN tx_hash VARCHAR(120) NULL,
    ADD UNIQUE KEY uk_payment_transaction_payment_key (payment_key);

CREATE TABLE payment_webhook_event (...);
```

```java
public void bindStablepaySession(String paymentKey, String paymentSessionId, String merchantKey, String payerAddress, String tokenAddress) {
    this.paymentKey = paymentKey;
    this.paymentSessionId = paymentSessionId;
    this.merchantKey = merchantKey;
    this.payerAddress = payerAddress;
    this.tokenAddress = tokenAddress;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :payment:test --tests "*PaymentTransactionEntityTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add payment/src/main/resources/db/migration/V5__add_stablepay_columns_and_webhook_event.sql \
  payment/src/main/java/com/wearhouse/payment/domain/payment/entity/PaymentTransactionEntity.java \
  payment/src/main/java/com/wearhouse/payment/domain/payment/entity/PaymentWebhookEventEntity.java \
  payment/src/main/java/com/wearhouse/payment/infra/jpa/repository/PaymentWebhookEventJpaRepository.java \
  payment/src/main/java/com/wearhouse/payment/infra/jpa/repository/PaymentWebhookEventRepository.java \
  payment/src/test/java/com/wearhouse/payment/domain/payment/entity/PaymentTransactionEntityTest.java
git commit -m "feat(payment): add stablepay columns and webhook dedupe table"
```

### Task 3: Add Pay Client + Internal Session Prepare API In Payment Service

**Files:**
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/controller/PaymentInternalController.java`
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/dto/request/StablepaySessionPrepareRequest.java`
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/dto/response/StablepaySessionPrepareResponse.java`
- Create: `payment/src/main/java/com/wearhouse/payment/infra/pay/PayCommandClient.java`
- Create: `payment/src/main/java/com/wearhouse/payment/support/config/PayClientProperties.java`
- Modify: `payment/src/main/resources/application.yml`
- Modify: `config-repo/payment-service.yml`
- Modify: `payment/src/main/java/com/wearhouse/payment/domain/payment/service/command/PaymentCommandService.java`
- Test: `payment/src/test/java/com/wearhouse/payment/domain/payment/controller/PaymentInternalControllerTest.java`

**Step 1: Write the failing test**

```java
@Test
void internal_prepare_returns_session_payload() {
    StablepaySessionPrepareResponse response = paymentInternalController.prepare(request());
    assertThat(response.paymentSessionId()).isNotBlank();
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :payment:test --tests "*PaymentInternalControllerTest"`  
Expected: FAIL because internal controller/DTO/client are missing.

**Step 3: Write minimal implementation**

```java
@PostMapping("/api/v1/internal/payments/stablepay/session")
public StablepaySessionPrepareResponse prepare(@RequestBody @Valid StablepaySessionPrepareRequest request) {
    return paymentCommandService.prepareStablepaySession(request);
}
```

```java
public record StablepaySessionPrepareResponse(
        String paymentKey,
        String paymentId,
        String paymentSessionId,
        String merchantKey,
        String nonce,
        String deadline,
        String payloadHash
) {}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :payment:test --tests "*PaymentInternalControllerTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add payment/src/main/java/com/wearhouse/payment/domain/payment/controller/PaymentInternalController.java \
  payment/src/main/java/com/wearhouse/payment/domain/payment/dto/request/StablepaySessionPrepareRequest.java \
  payment/src/main/java/com/wearhouse/payment/domain/payment/dto/response/StablepaySessionPrepareResponse.java \
  payment/src/main/java/com/wearhouse/payment/infra/pay/PayCommandClient.java \
  payment/src/main/java/com/wearhouse/payment/support/config/PayClientProperties.java \
  payment/src/main/resources/application.yml config-repo/payment-service.yml \
  payment/src/main/java/com/wearhouse/payment/domain/payment/service/command/PaymentCommandService.java \
  payment/src/test/java/com/wearhouse/payment/domain/payment/controller/PaymentInternalControllerTest.java
git commit -m "feat(payment): add internal stablepay session prepare api"
```

### Task 4: Add Pay Webhook Endpoint (HMAC Validation + Idempotency)

**Files:**
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/controller/PaymentWebhookController.java`
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/dto/request/PayWebhookRequest.java`
- Create: `payment/src/main/java/com/wearhouse/payment/domain/payment/service/command/PaymentWebhookService.java`
- Create: `payment/src/main/java/com/wearhouse/payment/support/security/PayWebhookSignatureVerifier.java`
- Modify: `payment/src/main/java/com/wearhouse/payment/support/security/PaymentSecurityConfig.java`
- Modify: `payment/src/main/resources/application.yml`
- Test: `payment/src/test/java/com/wearhouse/payment/domain/payment/service/command/PaymentWebhookServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void authorized_webhook_updates_transaction_and_publishes_event_once() {
    paymentWebhookService.handle(headers(), rawBody(), authorizedPayload());
    paymentWebhookService.handle(headers(), rawBody(), authorizedPayload());
    verify(paymentDomainEventPublisher, times(1)).publish(any());
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :payment:test --tests "*PaymentWebhookServiceTest"`  
Expected: FAIL because webhook components are missing.

**Step 3: Write minimal implementation**

```java
@PostMapping("/api/v1/payments/webhooks/pay")
public ResponseEntity<Void> receive(@RequestHeader("X-Webhook-Timestamp") String timestamp,
                                    @RequestHeader("X-Webhook-Signature") String signature,
                                    @RequestBody String rawBody) {
    paymentWebhookService.handle(timestamp, signature, rawBody);
    return ResponseEntity.ok().build();
}
```

```java
if ("payment.authorized".equals(eventType)) {
    publishPaymentAuthorized(...);
} else if ("payment.failed".equals(eventType)) {
    publishPaymentFailed(...);
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :payment:test --tests "*PaymentWebhookServiceTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add payment/src/main/java/com/wearhouse/payment/domain/payment/controller/PaymentWebhookController.java \
  payment/src/main/java/com/wearhouse/payment/domain/payment/dto/request/PayWebhookRequest.java \
  payment/src/main/java/com/wearhouse/payment/domain/payment/service/command/PaymentWebhookService.java \
  payment/src/main/java/com/wearhouse/payment/support/security/PayWebhookSignatureVerifier.java \
  payment/src/main/java/com/wearhouse/payment/support/security/PaymentSecurityConfig.java \
  payment/src/main/resources/application.yml \
  payment/src/test/java/com/wearhouse/payment/domain/payment/service/command/PaymentWebhookServiceTest.java
git commit -m "feat(payment): handle pay webhook with signature and idempotency"
```

### Task 5: Update Payment Prepare Consumer For `STABLEPAY`

**Files:**
- Modify: `payment/src/main/java/com/wearhouse/payment/domain/payment/service/command/PaymentCommandService.java`
- Modify: `payment/src/main/java/com/wearhouse/payment/domain/payment/entity/PaymentTransactionEntity.java`
- Test: `payment/src/test/java/com/wearhouse/payment/domain/payment/service/command/PaymentCommandServiceStablepayTest.java`

**Step 1: Write the failing test**

```java
@Test
void stablepay_prepare_should_create_pending_without_auto_authorize() {
    paymentCommandService.handlePaymentPrepareRequested(..., payloadWithStablepay());
    verify(paymentDomainEventPublisher, never()).publish(argThat(event -> "PaymentAuthorized".equals(event.getEventType())));
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :payment:test --tests "*PaymentCommandServiceStablepayTest"`  
Expected: FAIL because stablepay branch does not exist.

**Step 3: Write minimal implementation**

```java
if ("STABLEPAY".equals(normalizedMethod)) {
    paymentTransactionRepository.insertPending(...);
    paymentKafkaFlowMetrics.incrementPaymentDecision("stablepay_pending");
    return;
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :payment:test --tests "*PaymentCommandServiceStablepayTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add payment/src/main/java/com/wearhouse/payment/domain/payment/service/command/PaymentCommandService.java \
  payment/src/main/java/com/wearhouse/payment/domain/payment/entity/PaymentTransactionEntity.java \
  payment/src/test/java/com/wearhouse/payment/domain/payment/service/command/PaymentCommandServiceStablepayTest.java
git commit -m "feat(payment): keep stablepay in pending until webhook confirmation"
```

### Task 6: Orchestrate StablePay Session In `POST /api/v1/orders`

**Files:**
- Create: `order/src/main/java/com/wearhouse/order/domain/service/command/OrderCheckoutOrchestrationService.java`
- Create: `order/src/main/java/com/wearhouse/order/infra/payment/OrderPaymentClient.java`
- Create: `order/src/main/java/com/wearhouse/order/infra/payment/dto/StablepaySessionResponse.java`
- Modify: `order/src/main/java/com/wearhouse/order/domain/controller/OrderController.java`
- Modify: `order/src/main/java/com/wearhouse/order/domain/dto/response/OrderCreateResponse.java`
- Modify: `order/src/main/resources/application.yml`
- Modify: `config-repo/order-service.yml`
- Test: `order/src/test/java/com/wearhouse/order/domain/service/command/OrderCheckoutOrchestrationServiceTest.java`

**Step 1: Write the failing test**

```java
@Test
void create_order_with_stablepay_returns_payment_session_payload() {
    OrderCreateResponse response = orderCheckoutOrchestrationService.createOrderAndPrepareSession(requestStablepay());
    assertThat(response.paymentSessionId()).isNotBlank();
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :order:test --tests "*OrderCheckoutOrchestrationServiceTest"`  
Expected: FAIL because orchestration/service response fields are missing.

**Step 3: Write minimal implementation**

```java
public OrderCreateResponse createOrderAndPrepareSession(OrderCreateRequest request) {
    OrderCreateResponse created = orderCommandService.createOrder(request);
    if (request.paymentMethod() != PaymentMethod.STABLEPAY) return created;
    StablepaySessionResponse session = orderPaymentClient.prepareStablepaySession(created.orderNo(), ...);
    return created.withStablepaySession(session);
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :order:test --tests "*OrderCheckoutOrchestrationServiceTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add order/src/main/java/com/wearhouse/order/domain/service/command/OrderCheckoutOrchestrationService.java \
  order/src/main/java/com/wearhouse/order/infra/payment/OrderPaymentClient.java \
  order/src/main/java/com/wearhouse/order/infra/payment/dto/StablepaySessionResponse.java \
  order/src/main/java/com/wearhouse/order/domain/controller/OrderController.java \
  order/src/main/java/com/wearhouse/order/domain/dto/response/OrderCreateResponse.java \
  order/src/main/resources/application.yml config-repo/order-service.yml \
  order/src/test/java/com/wearhouse/order/domain/service/command/OrderCheckoutOrchestrationServiceTest.java
git commit -m "feat(order): include stablepay session in order create response"
```

### Task 7: Failure Policy And Saga Convergence (`PAYMENT_FAILED`)

**Files:**
- Modify: `order/src/main/java/com/wearhouse/order/domain/service/command/OrderSagaService.java`
- Modify: `order/src/main/java/com/wearhouse/order/domain/model/OrderSagaState.java`
- Modify: `order/src/test/java/com/wearhouse/order/domain/service/command/OrderSagaServiceFlowTest.java`
- Modify: `docs/plans/2026-03-08-order-inventory-payment-event-flow.md`

**Step 1: Write the failing test**

```java
@Test
void payment_failed_should_converge_to_payment_failed_terminal_state() {
    orderSagaService.onPaymentEvent("evt", "PaymentFailed", "topic", "key", "{}", payload());
    assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);
}
```

**Step 2: Run test to verify it fails**

Run: `./gradlew :order:test --tests "*OrderSagaServiceFlowTest"`  
Expected: FAIL if existing flow transitions to cancel path assumptions.

**Step 3: Write minimal implementation**

```java
private void handlePaymentFailed(...) {
    order.updateStatus(OrderStatus.PAYMENT_FAILED, reasonCode, null, null);
    transitionSaga(order.getId(), OrderSagaState.FAILED, eventId, reasonCode);
}
```

**Step 4: Run test to verify it passes**

Run: `./gradlew :order:test --tests "*OrderSagaServiceFlowTest"`  
Expected: PASS

**Step 5: Commit**

```bash
git add order/src/main/java/com/wearhouse/order/domain/service/command/OrderSagaService.java \
  order/src/main/java/com/wearhouse/order/domain/model/OrderSagaState.java \
  order/src/test/java/com/wearhouse/order/domain/service/command/OrderSagaServiceFlowTest.java \
  docs/plans/2026-03-08-order-inventory-payment-event-flow.md
git commit -m "refactor(order): converge payment failure to payment_failed state"
```

### Task 8: End-to-End Verification

**Files:**
- Modify: `docs/planning/test-matrix.md`
- Create: `docs/postman/stablepay-checkout.postman_collection.json`

**Step 1: Write failing integration scenario list**

```markdown
- create order with STABLEPAY should return session payload
- payment.authorized webhook should confirm order
- payment.failed webhook should keep order in PAYMENT_FAILED
```

**Step 2: Run verification commands**

Run: `./gradlew :order:test :payment:test`  
Expected: FAIL before final fixes are complete.

**Step 3: Execute final assertions**

```bash
./gradlew :common:test :order:test :payment:test :product:test
```

**Step 4: Confirm pass and collect evidence**

Expected: BUILD SUCCESSFUL and updated tests around stablepay/webhook paths.

**Step 5: Commit**

```bash
git add docs/planning/test-matrix.md docs/postman/stablepay-checkout.postman_collection.json
git commit -m "test: add stablepay checkout verification scenarios"
```

