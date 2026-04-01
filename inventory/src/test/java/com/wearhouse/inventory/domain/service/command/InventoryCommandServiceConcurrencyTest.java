package com.wearhouse.inventory.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.event.InventoryDomainEvent;
import com.wearhouse.inventory.domain.event.InventoryDomainEventPublisher;
import com.wearhouse.inventory.domain.repository.InventoryInboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryReservationRepository;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.buyer.service.BuyerInventoryCommandService;
import com.wearhouse.inventory.infra.product.InventoryProductStatusClient;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService.LockAcquireException;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.kafka.dto.InventoryReserveRequestedEvent;
import com.wearhouse.inventory.support.config.InventoryKafkaTopicsProperties;
import com.wearhouse.inventory.support.config.InventoryProperties;
import com.wearhouse.inventory.support.monitoring.InventoryFlowMetrics;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class InventoryCommandServiceConcurrencyTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private InventoryStockRepository inventoryStockRepository;
    @Mock
    private InventoryReservationRepository inventoryReservationRepository;
    @Mock
    private InventoryInboxRepository inventoryInboxRepository;
    @Mock
    private InventoryDomainEventPublisher inventoryDomainEventPublisher;
    @Mock
    private InventoryHotSkuLockService inventoryHotSkuLockService;
    @Mock
    private InventoryRedisStockCacheService inventoryRedisStockCacheService;
    @Mock
    private InventoryProductStatusClient inventoryProductStatusClient;
    @Mock
    private EntityManager entityManager;
    @Mock
    private InventoryFlowMetrics inventoryFlowMetrics;

    @Test
    void 핫SKU_락_획득_실패시_예약실패_이벤트를_발행한다() {
        BuyerInventoryCommandService service = newService("101", 3);
        InventoryReserveRequestedEvent payload = reservePayload(1L, "ORDER-1", 101L, 1);

        when(inventoryInboxRepository.tryReceive(anyString(), anyString()))
                .thenReturn(true);
        when(inventoryHotSkuLockService.withHotSkuLocks(anySet(), anyString(), any()))
                .thenThrow(new LockAcquireException("lock-fail"));

        service.onReserveRequested("evt-lock-fail", payload);

        ArgumentCaptor<InventoryDomainEvent> eventCaptor = ArgumentCaptor.forClass(InventoryDomainEvent.class);
        verify(inventoryDomainEventPublisher).publish(eventCaptor.capture());

        Map<String, Object> envelope = eventCaptor.getValue().toEnvelope();
        Map<String, Object> eventPayload = OBJECT_MAPPER.convertValue(
                envelope.get("payload"),
                new TypeReference<>() {}
        );

        assertThat(envelope.get("eventType")).isEqualTo("StockReserveFailed");
        assertThat(eventPayload.get("reasonCode")).isEqualTo("INVENTORY_409_003");

        verify(inventoryInboxRepository).markProcessed("evt-lock-fail", "inventory-command-consumer");
        verify(inventoryInboxRepository, never()).markFailed(anyString(), anyString());
        verify(inventoryStockRepository, never()).findBySkuId(anyLong());
    }

    @Test
    void 낙관락_재시도_소진시_예약실패_이벤트를_발행한다() {
        BuyerInventoryCommandService service = newService("999", 3);
        InventoryReserveRequestedEvent payload = reservePayload(2L, "ORDER-2", 201L, 1);

        when(inventoryInboxRepository.tryReceive(anyString(), anyString()))
                .thenReturn(true);
        when(inventoryHotSkuLockService.withHotSkuLocks(anySet(), anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        when(inventoryStockRepository.findBySkuId(eq(201L)))
                .thenAnswer(invocation -> Optional.of(InventoryStockEntity.of(
                        201L,
                        10,
                        777L,
                        9001L,
                        "Debug Product",
                        BigDecimal.valueOf(50000),
                        "OUTER",
                        "RELEASED",
                        "S",
                        "Black",
                        "https://cdn.example.com/main.jpg"
                )));
        when(inventoryStockRepository.saveAndFlush(any(InventoryStockEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(InventoryStockEntity.class, 201L));

        service.onReserveRequested("evt-optimistic-fail", payload);

        ArgumentCaptor<InventoryDomainEvent> eventCaptor = ArgumentCaptor.forClass(InventoryDomainEvent.class);
        verify(inventoryDomainEventPublisher).publish(eventCaptor.capture());

        Map<String, Object> envelope = eventCaptor.getValue().toEnvelope();
        Map<String, Object> eventPayload = OBJECT_MAPPER.convertValue(
                envelope.get("payload"),
                new TypeReference<>() {}
        );

        assertThat(envelope.get("eventType")).isEqualTo("StockReserveFailed");
        assertThat(eventPayload.get("reasonCode")).isEqualTo("INVENTORY_409_002");

        verify(inventoryStockRepository, times(3)).findBySkuId(201L);
        verify(inventoryStockRepository, times(3)).saveAndFlush(any(InventoryStockEntity.class));
        verify(entityManager, times(2)).clear();
        verify(inventoryRedisStockCacheService, never()).cacheAvailableQty(anyLong(), any());
        verify(inventoryInboxRepository).markProcessed("evt-optimistic-fail", "inventory-command-consumer");
        verify(inventoryInboxRepository, never()).markFailed(anyString(), anyString());
    }

    @Test
    void 핫SKU_미설정이면_모든SKU에_락을_획득한다() {
        BuyerInventoryCommandService service = newService("", 3);
        InventoryReserveRequestedEvent payload = reservePayload(3L, "ORDER-3", 301L, 1);
        InventoryStockEntity stock = InventoryStockEntity.of(
                301L,
                10,
                777L,
                9002L,
                "Lock Target Product",
                BigDecimal.valueOf(42000),
                "TOP",
                "RELEASED",
                "M",
                "Blue",
                "https://cdn.example.com/main-blue.jpg"
        );

        when(inventoryInboxRepository.tryReceive(anyString(), anyString()))
                .thenReturn(true);
        when(inventoryHotSkuLockService.withHotSkuLocks(anySet(), anyString(), any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(2)).get());
        when(inventoryStockRepository.findBySkuId(301L)).thenReturn(Optional.of(stock));
        when(inventoryStockRepository.saveAndFlush(any(InventoryStockEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.onReserveRequested("evt-lock-all", payload);

        verify(inventoryHotSkuLockService).withHotSkuLocks(anySet(), anyString(), any());
        verify(inventoryInboxRepository).markProcessed("evt-lock-all", "inventory-command-consumer");
    }

    private BuyerInventoryCommandService newService(String hotSkuRaw, int optimisticRetryCount) {
        InventoryProperties inventoryProperties = new InventoryProperties(
                15,
                optimisticRetryCount,
                30000L,
                200,
                hotSkuRaw,
                new InventoryProperties.Lock(1200L, 40L, "inventory:lock:sku:"),
                new InventoryProperties.Cache(30L, "inventory:stock:available:"),
                new InventoryProperties.Internal("wearhouse-inventory-internal-secret")
        );

        InventoryKafkaTopicsProperties inventoryKafkaTopicsProperties = new InventoryKafkaTopicsProperties(
                "wearhouse.inventory.command.v1",
                "wearhouse.inventory.event.v1",
                "wearhouse.order.event.v1"
        );

        BuyerInventoryCommandService service = new BuyerInventoryCommandService(
                inventoryStockRepository,
                inventoryReservationRepository,
                inventoryInboxRepository,
                inventoryDomainEventPublisher,
                inventoryHotSkuLockService,
                inventoryRedisStockCacheService,
                inventoryProductStatusClient,
                entityManager,
                inventoryProperties,
                inventoryKafkaTopicsProperties,
                inventoryFlowMetrics
        );
        return service;
    }

    private InventoryReserveRequestedEvent reservePayload(Long orderId, String orderNo, Long skuId, Integer quantity) {
        return new InventoryReserveRequestedEvent(
                orderId,
                orderNo,
                List.of(new InventoryReserveRequestedEvent.Item(
                        skuId,
                        skuId,
                        quantity
                ))
        );
    }
}
