package com.wearhouse.inventory.buyer.service;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.inventory.domain.entity.InventoryReservationEntity;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.event.InventoryDomainEvent;
import com.wearhouse.inventory.domain.event.InventoryDomainEventPublisher;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.domain.model.InventoryReservationStatus;
import com.wearhouse.inventory.domain.repository.InventoryInboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryReservationRepository;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.infra.product.InventoryProductStatusClient;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService.LockAcquireException;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.kafka.dto.InventoryReleaseRequestedEvent;
import com.wearhouse.inventory.kafka.dto.InventoryReserveRequestedEvent;
import com.wearhouse.inventory.kafka.dto.OrderConfirmedEvent;
import com.wearhouse.inventory.support.InventoryIdGenerator;
import com.wearhouse.inventory.support.config.InventoryKafkaTopicsProperties;
import com.wearhouse.inventory.support.config.InventoryProperties;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class BuyerInventoryCommandService {

    private static final String INVENTORY_COMMAND_CONSUMER = "inventory-command-consumer";
    private static final String INVENTORY_ORDER_CONSUMER = "inventory-order-consumer";

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryInboxRepository inventoryInboxRepository;
    private final InventoryDomainEventPublisher inventoryDomainEventPublisher;
    private final InventoryHotSkuLockService inventoryHotSkuLockService;
    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;
    private final InventoryProductStatusClient inventoryProductStatusClient;
    private final EntityManager entityManager;
    private final InventoryProperties inventoryProperties;
    private final InventoryKafkaTopicsProperties inventoryKafkaTopicsProperties;

    @WriteTx
    public void onReserveRequested(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            InventoryReserveRequestedEvent payload
    ) {
        // Inbox로 중복 메시지를 차단한다. (동일 eventId 재수신은 무시)
        boolean received = inventoryInboxRepository.tryReceive(
                eventId,
                INVENTORY_COMMAND_CONSUMER,
                "InventoryReserveRequested",
                topic,
                partitionKey,
                rawPayload
        );
        if (!received) {
            return;
        }

        InventoryReserveCommand command = null;
        try {
            command = toReserveCommand(payload);
            // 재고 예약은 hot SKU 락 구간에서 처리한다.
            List<ReservationLineResult> results = reserve(command, eventId);
            publishStockReserved(command, results);
            inventoryInboxRepository.markProcessed(eventId, INVENTORY_COMMAND_CONSUMER);
        } catch (ErrorException exception) {
            // 비즈니스 실패는 실패 이벤트를 발행하고 메시지는 처리완료로 기록한다.
            Long fallbackOrderId = command == null ? payload.orderId() : command.orderId();
            String fallbackOrderNo = command == null ? payload.orderNo() : command.orderNo();
            publishStockReserveFailed(
                    fallbackOrderId,
                    fallbackOrderNo,
                    exception.errorCode().code(),
                    exception.errorCode().message()
            );
            inventoryInboxRepository.markProcessed(eventId, INVENTORY_COMMAND_CONSUMER);
        } catch (Exception exception) {
            inventoryInboxRepository.markFailed(
                    eventId,
                    INVENTORY_COMMAND_CONSUMER,
                    "CONSUME_FAIL",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @WriteTx
    public void onReleaseRequested(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            InventoryReleaseRequestedEvent payload
    ) {
        // 주문 보상/취소 시 재고 해제 이벤트를 처리한다.
        boolean received = inventoryInboxRepository.tryReceive(
                eventId,
                INVENTORY_COMMAND_CONSUMER,
                "InventoryReleaseRequested",
                topic,
                partitionKey,
                rawPayload
        );
        if (!received) {
            return;
        }

        try {
            InventoryReleaseCommand command = toReleaseCommand(payload);
            int releasedCount = release(command);
            publishInventoryReleased(command, releasedCount);
            inventoryInboxRepository.markProcessed(eventId, INVENTORY_COMMAND_CONSUMER);
        } catch (Exception exception) {
            inventoryInboxRepository.markFailed(
                    eventId,
                    INVENTORY_COMMAND_CONSUMER,
                    "CONSUME_FAIL",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @WriteTx
    public void onOrderConfirmed(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            OrderConfirmedEvent payload
    ) {
        // 주문 확정 이벤트 수신 시 RESERVED 수량을 최종 차감(CONFIRMED) 처리한다.
        boolean received = inventoryInboxRepository.tryReceive(
                eventId,
                INVENTORY_ORDER_CONSUMER,
                "OrderConfirmed",
                topic,
                partitionKey,
                rawPayload
        );
        if (!received) {
            return;
        }

        try {
            InventoryOrderConfirmCommand command = toOrderConfirmCommand(payload);
            confirm(command);
            inventoryInboxRepository.markProcessed(eventId, INVENTORY_ORDER_CONSUMER);
        } catch (Exception exception) {
            inventoryInboxRepository.markFailed(
                    eventId,
                    INVENTORY_ORDER_CONSUMER,
                    "CONSUME_FAIL",
                    exception.getMessage()
            );
            throw exception;
        }
    }

    @WriteTx
    public int releaseExpiredReservations() {
        LocalDateTime now = LocalDateTime.now();
        List<InventoryReservationEntity> expiredReservations = inventoryReservationRepository
                .findByStatusAndExpiresAtLessThanEqualOrderByIdAsc(
                        InventoryReservationStatus.RESERVED,
                        now,
                        PageRequest.of(0, inventoryProperties.reservationExpireBatchSize())
                );

        int releasedCount = 0;
        for (InventoryReservationEntity reservation : expiredReservations) {
            if (!markReservationReleasedIfReserved(reservation.getId(), now)) {
                continue;
            }
            releaseStockWithRetry(reservation.getSkuId(), reservation.getQuantity());
            releasedCount++;
        }
        return releasedCount;
    }

    private List<ReservationLineResult> reserve(InventoryReserveCommand command, String sourceEventId) {
        Map<Long, Integer> quantitiesBySku = new LinkedHashMap<>();
        for (ReserveLine line : command.lines()) {
            quantitiesBySku.merge(line.skuId(), line.quantity(), Integer::sum);
        }

        try {
            // 같은 SKU에 대한 동시 예약 경쟁을 막기 위해 SKU 단위 분산락을 획득한다.
            return inventoryHotSkuLockService.withHotSkuLocks(
                    quantitiesBySku.keySet(),
                    sourceEventId,
                    () -> reserveLines(command, sourceEventId, quantitiesBySku)
            );
        } catch (LockAcquireException exception) {
            throw new ErrorException(InventoryErrorCode.HOT_SKU_LOCK_ACQUIRE_FAILED);
        }
    }

    private int release(InventoryReleaseCommand command) {
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findByOrderIdAndStatus(
                command.orderId(),
                InventoryReservationStatus.RESERVED
        );

        Map<Long, Integer> quantitiesBySku = aggregateQuantitiesBySku(reservations);
        // 해제도 동일 SKU 락 구간에서 처리해 reserve/confirm과 경합하지 않도록 한다.
        return inventoryHotSkuLockService.withHotSkuLocks(
                quantitiesBySku.keySet(),
                "release:" + command.orderId() + ":" + InventoryIdGenerator.newEventId(),
                () -> releaseReservedStocks(reservations)
        );
    }

    private int confirm(InventoryOrderConfirmCommand command) {
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findByOrderIdAndStatus(
                command.orderId(),
                InventoryReservationStatus.RESERVED
        );

        Map<Long, Integer> quantitiesBySku = aggregateQuantitiesBySku(reservations);
        // 확정 차감도 동일 SKU 락 구간에서 처리해 정합성을 유지한다.
        return inventoryHotSkuLockService.withHotSkuLocks(
                quantitiesBySku.keySet(),
                "confirm:" + command.orderId() + ":" + InventoryIdGenerator.newEventId(),
                () -> confirmReservedStocks(reservations)
        );
    }

    private boolean markReservationReleasedIfReserved(Long reservationId, LocalDateTime releasedAt) {
        return inventoryReservationRepository.markReleasedIfReserved(reservationId, releasedAt) > 0;
    }

    private boolean markReservationConfirmedIfReserved(Long reservationId, LocalDateTime confirmedAt) {
        return inventoryReservationRepository.markConfirmedIfReserved(reservationId, confirmedAt) > 0;
    }

    private List<ReservationLineResult> reserveLines(
            InventoryReserveCommand command,
            String sourceEventId,
            Map<Long, Integer> quantitiesBySku
    ) {
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(inventoryProperties.reservationHoldMinutes());
        List<ReservationLineResult> results = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : quantitiesBySku.entrySet()) {
            Long skuId = entry.getKey();
            Integer quantity = entry.getValue();
            reserveStockWithRetry(skuId, quantity);

            String reservationId = InventoryIdGenerator.newReservationId();
            inventoryReservationRepository.save(InventoryReservationEntity.reserved(
                    reservationId,
                    sourceEventId,
                    command.orderId(),
                    command.orderNo(),
                    skuId,
                    quantity,
                    expiresAt
            ));
            results.add(new ReservationLineResult(reservationId, skuId, quantity, expiresAt));
        }
        return results;
    }

    private Map<Long, Integer> aggregateQuantitiesBySku(List<InventoryReservationEntity> reservations) {
        Map<Long, Integer> quantitiesBySku = new LinkedHashMap<>();
        if (reservations == null || reservations.isEmpty()) {
            return quantitiesBySku;
        }
        for (InventoryReservationEntity reservation : reservations) {
            quantitiesBySku.merge(reservation.getSkuId(), reservation.getQuantity(), Integer::sum);
        }
        return quantitiesBySku;
    }

    private int releaseReservedStocks(List<InventoryReservationEntity> reservations) {
        LocalDateTime releasedAt = LocalDateTime.now();
        int releasedCount = 0;
        for (InventoryReservationEntity reservation : reservations) {
            if (!markReservationReleasedIfReserved(reservation.getId(), releasedAt)) {
                continue;
            }
            releaseStockWithRetry(reservation.getSkuId(), reservation.getQuantity());
            releasedCount++;
        }
        return releasedCount;
    }

    private int confirmReservedStocks(List<InventoryReservationEntity> reservations) {
        LocalDateTime confirmedAt = LocalDateTime.now();
        int confirmedCount = 0;
        Set<Long> soldOutCandidateProductIds = new LinkedHashSet<>();
        for (InventoryReservationEntity reservation : reservations) {
            if (!markReservationConfirmedIfReserved(reservation.getId(), confirmedAt)) {
                continue;
            }
            InventoryStockEntity confirmedStock = confirmStockWithRetry(reservation.getSkuId(), reservation.getQuantity());
            if (confirmedStock.getProductId() != null && confirmedStock.getAvailableQty() <= 0) {
                soldOutCandidateProductIds.add(confirmedStock.getProductId());
            }
            confirmedCount++;
        }
        syncSoldOutProducts(soldOutCandidateProductIds);
        return confirmedCount;
    }

    private void reserveStockWithRetry(Long skuId, Integer quantity) {
        int retryCount = inventoryProperties.optimisticRetryCount();
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                    .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
            if (!stock.canReserve(quantity)) {
                throw new ErrorException(InventoryErrorCode.OUT_OF_STOCK);
            }

            stock.reserve(quantity);
            try {
                inventoryStockRepository.saveAndFlush(stock);
                inventoryRedisStockCacheService.cacheAvailableQty(stock.getSkuId(), stock.getAvailableQty());
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == retryCount) {
                    throw new ErrorException(InventoryErrorCode.OPTIMISTIC_CONFLICT);
                }
                entityManager.clear();
            }
        }
    }

    private void releaseStockWithRetry(Long skuId, Integer quantity) {
        int retryCount = inventoryProperties.optimisticRetryCount();
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                    .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
            stock.release(quantity);
            try {
                inventoryStockRepository.saveAndFlush(stock);
                inventoryRedisStockCacheService.cacheAvailableQty(stock.getSkuId(), stock.getAvailableQty());
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == retryCount) {
                    throw new ErrorException(InventoryErrorCode.OPTIMISTIC_CONFLICT);
                }
                entityManager.clear();
            }
        }
    }

    private InventoryStockEntity confirmStockWithRetry(Long skuId, Integer quantity) {
        int retryCount = inventoryProperties.optimisticRetryCount();
        for (int attempt = 1; attempt <= retryCount; attempt++) {
            InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                    .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
            stock.confirm(quantity);
            try {
                inventoryStockRepository.saveAndFlush(stock);
                inventoryRedisStockCacheService.cacheAvailableQty(stock.getSkuId(), stock.getAvailableQty());
                return stock;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == retryCount) {
                    throw new ErrorException(InventoryErrorCode.OPTIMISTIC_CONFLICT);
                }
                entityManager.clear();
            }
        }
        throw new ErrorException(InventoryErrorCode.OPTIMISTIC_CONFLICT);
    }

    private void syncSoldOutProducts(Set<Long> soldOutCandidateProductIds) {
        if (soldOutCandidateProductIds == null || soldOutCandidateProductIds.isEmpty()) {
            return;
        }

        List<Long> soldOutProductIds = soldOutCandidateProductIds.stream()
                .filter(this::isProductSoldOut)
                .toList();
        inventoryProductStatusClient.markProductsSoldOut(soldOutProductIds);
    }

    private boolean isProductSoldOut(Long productId) {
        return !inventoryStockRepository.existsByProductIdAndAvailableQtyGreaterThan(productId, 0);
    }

    private void publishStockReserved(InventoryReserveCommand command, List<ReservationLineResult> results) {
        List<Map<String, Object>> reservationPayload = new ArrayList<>();
        for (ReservationLineResult result : results) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("reservationId", result.reservationId());
            line.put("skuId", result.skuId());
            line.put("quantity", result.quantity());
            line.put("expiresAt", result.expiresAt());
            reservationPayload.add(line);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", command.orderId());
        payload.put("orderNo", command.orderNo());
        payload.put("reservations", reservationPayload);
        payload.put("reservedAt", LocalDateTime.now());

        InventoryDomainEvent event = InventoryDomainEvent.builder()
                .eventId(InventoryIdGenerator.newEventId())
                .eventType("StockReserved")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(command.orderId()))
                .topic(inventoryKafkaTopicsProperties.inventoryEventTopic())
                .partitionKey(String.valueOf(command.orderId()))
                .payload(payload)
                .build();
        // 재고 예약 성공을 order saga로 전달한다.
        inventoryDomainEventPublisher.publish(event);
    }

    private void publishStockReserveFailed(Long orderId, String orderNo, String reasonCode, String reasonMessage) {
        if (orderId == null) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("reasonCode", reasonCode);
        payload.put("reasonMessage", reasonMessage);

        InventoryDomainEvent event = InventoryDomainEvent.builder()
                .eventId(InventoryIdGenerator.newEventId())
                .eventType("StockReserveFailed")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(orderId))
                .topic(inventoryKafkaTopicsProperties.inventoryEventTopic())
                .partitionKey(String.valueOf(orderId))
                .payload(payload)
                .build();
        // 재고 예약 실패를 order saga로 전달한다.
        inventoryDomainEventPublisher.publish(event);
    }

    private void publishInventoryReleased(InventoryReleaseCommand command, int releasedCount) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", command.orderId());
        payload.put("orderNo", command.orderNo());
        payload.put("reasonCode", command.reasonCode());
        payload.put("releasedCount", releasedCount);
        payload.put("releasedAt", LocalDateTime.now());

        InventoryDomainEvent event = InventoryDomainEvent.builder()
                .eventId(InventoryIdGenerator.newEventId())
                .eventType("InventoryReleased")
                .aggregateType("ORDER")
                .aggregateId(String.valueOf(command.orderId()))
                .topic(inventoryKafkaTopicsProperties.inventoryEventTopic())
                .partitionKey(String.valueOf(command.orderId()))
                .payload(payload)
                .build();
        // 보상 해제 완료를 order saga로 전달한다.
        inventoryDomainEventPublisher.publish(event);
    }

    private InventoryReserveCommand toReserveCommand(InventoryReserveRequestedEvent payload) {
        Long orderId = payload.orderId();
        if (orderId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String orderNo = payload.orderNo();

        List<ReserveLine> lines = new ArrayList<>();
        if (payload.items() == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        for (InventoryReserveRequestedEvent.Item item : payload.items()) {
            Long optionId = item.optionId();
            Long productId = item.productId();
            Long skuId = optionId == null ? productId : optionId;
            Integer quantity = item.quantity();

            if (skuId == null || quantity == null || quantity <= 0) {
                throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
            }
            lines.add(new ReserveLine(skuId, quantity));
        }

        if (lines.isEmpty()) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        return new InventoryReserveCommand(orderId, orderNo, lines);
    }

    private InventoryReleaseCommand toReleaseCommand(InventoryReleaseRequestedEvent payload) {
        Long orderId = payload.orderId();
        if (orderId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String orderNo = payload.orderNo();
        String reasonCode = payload.reasonCode();
        if (reasonCode == null || reasonCode.isBlank()) {
            reasonCode = "UNKNOWN";
        }
        return new InventoryReleaseCommand(orderId, orderNo, reasonCode);
    }

    private InventoryOrderConfirmCommand toOrderConfirmCommand(OrderConfirmedEvent payload) {
        Long orderId = payload.orderId();
        if (orderId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String orderNo = payload.orderNo();
        return new InventoryOrderConfirmCommand(orderId, orderNo);
    }

    private record InventoryReserveCommand(Long orderId, String orderNo, List<ReserveLine> lines) {
    }

    private record InventoryReleaseCommand(Long orderId, String orderNo, String reasonCode) {
    }

    private record InventoryOrderConfirmCommand(Long orderId, String orderNo) {
    }

    private record ReserveLine(Long skuId, Integer quantity) {
    }

    private record ReservationLineResult(String reservationId, Long skuId, Integer quantity, LocalDateTime expiresAt) {
    }
}
