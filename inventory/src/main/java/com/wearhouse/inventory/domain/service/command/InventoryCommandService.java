package com.wearhouse.inventory.domain.service.command;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.entity.InventoryReservationEntity;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.event.InventoryDomainEvent;
import com.wearhouse.inventory.domain.event.InventoryDomainEventPublisher;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpdateRequest;
import com.wearhouse.inventory.domain.dto.request.InventoryStockUpsertRequest;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.domain.model.InventoryReservationStatus;
import com.wearhouse.inventory.domain.repository.InventoryInboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryReservationRepository;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.infra.product.InventoryProductStatusClient;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService.SkuLockHandle;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.support.InventoryIdGenerator;
import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import jakarta.persistence.OptimisticLockException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import com.wearhouse.common.global.transactional.WriteTx;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class InventoryCommandService {

    private static final String INVENTORY_COMMAND_CONSUMER = "inventory-command-consumer";
    private static final String INVENTORY_ORDER_CONSUMER = "inventory-order-consumer";

    private final InventoryStockRepository inventoryStockRepository;
    private final InventoryReservationRepository inventoryReservationRepository;
    private final InventoryInboxRepository inventoryInboxRepository;
    private final InventoryDomainEventPublisher inventoryDomainEventPublisher;
    private final InventoryHotSkuLockService inventoryHotSkuLockService;
    private final InventoryRedisStockCacheService inventoryRedisStockCacheService;
    private final InventoryProductStatusClient inventoryProductStatusClient;
    private final InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;
    private final EntityManager entityManager;
    @Value("${wearhouse.kafka.inventory-event-topic:wearhouse.inventory.event.v1}")
    private String inventoryEventTopic;
    @Value("${wearhouse.inventory.reservation-hold-minutes:15}")
    private int reservationHoldMinutes;
    @Value("${wearhouse.inventory.optimistic-retry-count:3}")
    private int optimisticRetryCount;
    @Value("${wearhouse.inventory.reservation-expire-batch-size:200}")
    private int reservationExpireBatchSize;
    @Value("${wearhouse.inventory.hot-skus:}")
    private String hotSkuRaw;
    private Set<Long> hotSkuIds = Set.of();

    @PostConstruct
    void initializeHotSkuIds() {
        hotSkuIds = parseHotSkuIds(hotSkuRaw);
    }

    @WriteTx
    public InventoryStockResponse upsertStock(InventoryStockUpsertRequest request) {
        validateUpsertRequest(request);

        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(request.skuId())
                .orElseGet(() -> InventoryStockEntity.create(
                        request.skuId(),
                        request.availableQty(),
                        request.sellerId(),
                        request.productId(),
                        request.productName(),
                        request.productPrice(),
                        request.category(),
                        request.size(),
                        request.color(),
                        request.mainImageUrl()
                ));

        stock.setAvailableQty(request.availableQty());
        stock.setStatus(request.status());
        stock.updateSnapshot(
                request.sellerId(),
                request.productId(),
                request.productName(),
                request.productPrice(),
                request.category(),
                request.size(),
                request.color(),
                request.mainImageUrl()
        );
        InventoryStockEntity saved = inventoryStockRepository.save(stock);
        inventoryRedisStockCacheService.cacheAvailableQty(saved.getSkuId(), saved.getAvailableQty());
        return InventoryStockResponse.from(saved);
    }

    @WriteTx
    public InventoryStockResponse updateSellerInventory(LoginUser currentUser, Long skuId, InventoryStockUpdateRequest request) {
        Long sellerId = requireSeller(currentUser);
        if (request == null || (request.availableQty() == null && request.status() == null)) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        if (request.availableQty() != null && request.availableQty() < 0) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        if (request.status() != null && !isValidStatus(request.status())) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }

        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
        if (!sellerId.equals(stock.getSellerId())) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }

        if (request.availableQty() != null) {
            stock.setAvailableQty(request.availableQty());
        }
        if (request.status() != null) {
            stock.setStatus(request.status());
        }

        InventoryStockEntity saved = inventoryStockRepository.save(stock);
        inventoryRedisStockCacheService.cacheAvailableQty(saved.getSkuId(), saved.getAvailableQty());
        return InventoryStockResponse.from(saved);
    }

    @WriteTx
    public void deleteStocksByProductId(LoginUser currentUser, Long productId) {
        if (productId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        Long sellerId = requireSeller(currentUser);
        List<InventoryStockEntity> stocks = inventoryStockRepository.findAllByProductIdAndSellerId(productId, sellerId);
        if (stocks.isEmpty()) {
            return;
        }

        List<Long> skuIds = stocks.stream()
                .map(InventoryStockEntity::getSkuId)
                .toList();
        inventoryStockRepository.deleteAllInBatch(stocks);
        inventoryRedisStockCacheService.evictAvailableQtyBySkuIds(skuIds);
    }

    @WriteTx
    public void onReserveRequested(
            String eventId,
            String topic,
            String partitionKey,
            String rawPayload,
            Map<String, Object> payload
    ) {
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
            List<ReservationLineResult> results = reserve(command, eventId);
            publishStockReserved(command, results);
            inventoryInboxRepository.markProcessed(eventId, INVENTORY_COMMAND_CONSUMER);
        } catch (ErrorException exception) {
            Long fallbackOrderId = command == null ? asLong(payload.get("orderId")) : command.orderId();
            String fallbackOrderNo = command == null ? asString(payload.get("orderNo")) : command.orderNo();
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
            Map<String, Object> payload
    ) {
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
            Map<String, Object> payload
    ) {
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
                        PageRequest.of(0, reservationExpireBatchSize)
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

        List<SkuLockHandle> lockHandles = acquireHotSkuLocks(quantitiesBySku.keySet(), sourceEventId);
        try {
            LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(reservationHoldMinutes);
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
        } finally {
            releaseHotSkuLocksAfterTransaction(lockHandles);
        }
    }

    private int release(InventoryReleaseCommand command) {
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findByOrderIdAndStatus(
                command.orderId(),
                InventoryReservationStatus.RESERVED
        );

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

    private int confirm(InventoryOrderConfirmCommand command) {
        List<InventoryReservationEntity> reservations = inventoryReservationRepository.findByOrderIdAndStatus(
                command.orderId(),
                InventoryReservationStatus.RESERVED
        );

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

    private boolean markReservationReleasedIfReserved(Long reservationId, LocalDateTime releasedAt) {
        return inventoryReservationRepository.markReleasedIfReserved(reservationId, releasedAt) > 0;
    }

    private boolean markReservationConfirmedIfReserved(Long reservationId, LocalDateTime confirmedAt) {
        return inventoryReservationRepository.markConfirmedIfReserved(reservationId, confirmedAt) > 0;
    }

    private List<SkuLockHandle> acquireHotSkuLocks(Set<Long> skuIds, String sourceEventId) {
        List<Long> lockTargets = selectLockTargets(skuIds);
        if (lockTargets.isEmpty()) {
            return List.of();
        }

        String ownerToken = sourceEventId + ":" + InventoryIdGenerator.newEventId();
        List<SkuLockHandle> lockHandles = new ArrayList<>();
        try {
            for (Long skuId : lockTargets) {
                SkuLockHandle handle = inventoryHotSkuLockService.acquire(skuId, ownerToken);
                if (handle == null) {
                    inventoryKafkaFlowMetrics.incrementConcurrencyGuard("redisson_lock", "acquire_fail");
                    throw new ErrorException(InventoryErrorCode.HOT_SKU_LOCK_ACQUIRE_FAILED);
                }
                lockHandles.add(handle);
            }
            return lockHandles;
        } catch (RuntimeException exception) {
            releaseHotSkuLocksNow(lockHandles);
            throw exception;
        }
    }

    private List<Long> selectLockTargets(Set<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        if (hotSkuIds.isEmpty()) {
            return skuIds.stream().sorted().toList();
        }
        return skuIds.stream()
                .filter(hotSkuIds::contains)
                .sorted()
                .toList();
    }

    private void releaseHotSkuLocksAfterTransaction(List<SkuLockHandle> lockHandles) {
        if (lockHandles == null || lockHandles.isEmpty()) {
            return;
        }
        List<SkuLockHandle> releaseTargets = new ArrayList<>(lockHandles);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    releaseHotSkuLocksNow(releaseTargets);
                }
            });
            return;
        }
        releaseHotSkuLocksNow(releaseTargets);
    }

    private void releaseHotSkuLocksNow(List<SkuLockHandle> lockHandles) {
        if (lockHandles == null || lockHandles.isEmpty()) {
            return;
        }
        List<SkuLockHandle> releaseTargets = new ArrayList<>(lockHandles);
        Collections.reverse(releaseTargets);
        for (SkuLockHandle lockHandle : releaseTargets) {
            inventoryHotSkuLockService.release(lockHandle);
        }
    }

    private void reserveStockWithRetry(Long skuId, Integer quantity) {
        for (int attempt = 1; attempt <= optimisticRetryCount; attempt++) {
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
                if (attempt == optimisticRetryCount) {
                    inventoryKafkaFlowMetrics.incrementConcurrencyGuard("optimistic_lock", "conflict");
                    throw new ErrorException(InventoryErrorCode.OPTIMISTIC_CONFLICT);
                }
                entityManager.clear();
            }
        }
    }

    private void releaseStockWithRetry(Long skuId, Integer quantity) {
        for (int attempt = 1; attempt <= optimisticRetryCount; attempt++) {
            InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                    .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
            stock.release(quantity);
            try {
                inventoryStockRepository.saveAndFlush(stock);
                inventoryRedisStockCacheService.cacheAvailableQty(stock.getSkuId(), stock.getAvailableQty());
                return;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == optimisticRetryCount) {
                    inventoryKafkaFlowMetrics.incrementConcurrencyGuard("optimistic_lock", "conflict");
                    throw new ErrorException(InventoryErrorCode.OPTIMISTIC_CONFLICT);
                }
                entityManager.clear();
            }
        }
    }

    private InventoryStockEntity confirmStockWithRetry(Long skuId, Integer quantity) {
        for (int attempt = 1; attempt <= optimisticRetryCount; attempt++) {
            InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                    .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
            stock.confirm(quantity);
            try {
                inventoryStockRepository.saveAndFlush(stock);
                inventoryRedisStockCacheService.cacheAvailableQty(stock.getSkuId(), stock.getAvailableQty());
                return stock;
            } catch (ObjectOptimisticLockingFailureException | OptimisticLockException exception) {
                if (attempt == optimisticRetryCount) {
                    inventoryKafkaFlowMetrics.incrementConcurrencyGuard("optimistic_lock", "conflict");
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
                .topic(inventoryEventTopic)
                .partitionKey(String.valueOf(command.orderId()))
                .payload(payload)
                .build();
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
                .topic(inventoryEventTopic)
                .partitionKey(String.valueOf(orderId))
                .payload(payload)
                .build();
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
                .topic(inventoryEventTopic)
                .partitionKey(String.valueOf(command.orderId()))
                .payload(payload)
                .build();
        inventoryDomainEventPublisher.publish(event);
    }

    private InventoryReserveCommand toReserveCommand(Map<String, Object> payload) {
        Long orderId = asLong(payload.get("orderId"));
        if (orderId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String orderNo = asString(payload.get("orderNo"));

        List<ReserveLine> lines = new ArrayList<>();
        for (Map<String, Object> item : toMapList(payload.get("items"))) {
            Long optionId = asLong(item.get("optionId"));
            Long productId = asLong(item.get("productId"));
            Long skuId = optionId == null ? productId : optionId;
            Integer quantity = asInt(item.get("quantity"));

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

    private InventoryReleaseCommand toReleaseCommand(Map<String, Object> payload) {
        Long orderId = asLong(payload.get("orderId"));
        if (orderId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String orderNo = asString(payload.get("orderNo"));
        String reasonCode = asString(payload.get("reasonCode"));
        if (reasonCode == null || reasonCode.isBlank()) {
            reasonCode = "UNKNOWN";
        }
        return new InventoryReleaseCommand(orderId, orderNo, reasonCode);
    }

    private InventoryOrderConfirmCommand toOrderConfirmCommand(Map<String, Object> payload) {
        Long orderId = asLong(payload.get("orderId"));
        if (orderId == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        String orderNo = asString(payload.get("orderNo"));
        return new InventoryOrderConfirmCommand(orderId, orderNo);
    }

    private Set<Long> parseHotSkuIds(String hotSkuRaw) {
        if (hotSkuRaw == null || hotSkuRaw.isBlank()) {
            return Set.of();
        }

        Set<Long> hotSkus = new LinkedHashSet<>();
        for (String token : hotSkuRaw.split(",")) {
            if (token == null || token.isBlank()) {
                continue;
            }
            try {
                hotSkus.add(Long.parseLong(token.trim()));
            } catch (NumberFormatException exception) {
                throw new IllegalStateException("wearhouse.inventory.hot-skus 설정이 올바르지 않습니다.", exception);
            }
        }
        return hotSkus;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toMapList(Object value) {
        if (value instanceof List<?> list) {
            List<Map<String, Object>> result = new ArrayList<>();
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> map)) {
                    throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
                }
                result.add((Map<String, Object>) map);
            }
            return result;
        }
        throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
    }

    private Long asLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Long.parseLong(stringValue);
            } catch (NumberFormatException exception) {
                throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
            }
        }
        return null;
    }

    private Integer asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            try {
                return Integer.parseInt(stringValue);
            } catch (NumberFormatException exception) {
                throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
            }
        }
        return null;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private void validateUpsertRequest(InventoryStockUpsertRequest request) {
        if (request == null
                || request.skuId() == null
                || request.availableQty() == null
                || request.availableQty() < 0
                || request.sellerId() == null
                || request.productId() == null
                || isBlank(request.productName())
                || request.productPrice() == null
                || request.productPrice().compareTo(BigDecimal.ZERO) < 0
                || isBlank(request.category())
                || isBlank(request.size())
                || isBlank(request.color())
                || isBlank(request.mainImageUrl())
                || request.status() == null
                || !isValidStatus(request.status())) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !currentUser.isSeller()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return currentUser.userId();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isValidStatus(Integer status) {
        return status == InventoryStockEntity.STATUS_SOLD_OUT || status == InventoryStockEntity.STATUS_ON_SALE;
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
