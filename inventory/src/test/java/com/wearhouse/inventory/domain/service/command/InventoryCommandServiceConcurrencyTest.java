package com.wearhouse.inventory.domain.service.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.event.InventoryDomainEvent;
import com.wearhouse.inventory.domain.event.InventoryDomainEventPublisher;
import com.wearhouse.inventory.infra.jpa.repository.InventoryInboxRepository;
import com.wearhouse.inventory.infra.jpa.repository.InventoryReservationJpaRepository;
import com.wearhouse.inventory.infra.jpa.repository.InventoryStockJpaRepository;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

@ExtendWith(MockitoExtension.class)
class InventoryCommandServiceConcurrencyTest {

    @Mock
    private InventoryStockJpaRepository inventoryStockJpaRepository;
    @Mock
    private InventoryReservationJpaRepository inventoryReservationJpaRepository;
    @Mock
    private InventoryInboxRepository inventoryInboxRepository;
    @Mock
    private InventoryDomainEventPublisher inventoryDomainEventPublisher;
    @Mock
    private InventoryHotSkuLockService inventoryHotSkuLockService;
    @Mock
    private InventoryRedisStockCacheService inventoryRedisStockCacheService;
    @Mock
    private InventoryKafkaFlowMetrics inventoryKafkaFlowMetrics;
    @Mock
    private EntityManager entityManager;

    @Test
    void 핫SKU_락_획득_실패시_예약실패_이벤트를_발행한다() {
        InventoryCommandService service = newService("101", 3);
        Map<String, Object> payload = reservePayload(1L, "ORDER-1", 101L, 1);

        when(inventoryInboxRepository.tryReceive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(inventoryHotSkuLockService.acquire(eq(101L), anyString())).thenReturn(null);

        service.onReserveRequested("evt-lock-fail", "inventory-command", "1", "{}", payload);

        ArgumentCaptor<InventoryDomainEvent> eventCaptor = ArgumentCaptor.forClass(InventoryDomainEvent.class);
        verify(inventoryDomainEventPublisher).publish(eventCaptor.capture());

        Map<String, Object> envelope = eventCaptor.getValue().toEnvelope();
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) envelope.get("payload");

        assertThat(envelope.get("eventType")).isEqualTo("StockReserveFailed");
        assertThat(eventPayload.get("reasonCode")).isEqualTo("INVENTORY_409_003");

        verify(inventoryInboxRepository).markProcessed("evt-lock-fail", "inventory-command-consumer");
        verify(inventoryInboxRepository, never()).markFailed(anyString(), anyString(), anyString(), anyString());
        verify(inventoryStockJpaRepository, never()).findBySkuId(anyLong());
    }

    @Test
    void 낙관락_재시도_소진시_예약실패_이벤트를_발행한다() {
        InventoryCommandService service = newService("", 3);
        Map<String, Object> payload = reservePayload(2L, "ORDER-2", 201L, 1);

        when(inventoryInboxRepository.tryReceive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(inventoryStockJpaRepository.findBySkuId(eq(201L)))
                .thenAnswer(invocation -> Optional.of(InventoryStockEntity.create(201L, 10)));
        when(inventoryStockJpaRepository.saveAndFlush(any(InventoryStockEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(InventoryStockEntity.class, 201L));

        service.onReserveRequested("evt-optimistic-fail", "inventory-command", "2", "{}", payload);

        ArgumentCaptor<InventoryDomainEvent> eventCaptor = ArgumentCaptor.forClass(InventoryDomainEvent.class);
        verify(inventoryDomainEventPublisher).publish(eventCaptor.capture());

        Map<String, Object> envelope = eventCaptor.getValue().toEnvelope();
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) envelope.get("payload");

        assertThat(envelope.get("eventType")).isEqualTo("StockReserveFailed");
        assertThat(eventPayload.get("reasonCode")).isEqualTo("INVENTORY_409_002");

        verify(inventoryStockJpaRepository, times(3)).findBySkuId(201L);
        verify(inventoryStockJpaRepository, times(3)).saveAndFlush(any(InventoryStockEntity.class));
        verify(entityManager, times(2)).clear();
        verify(inventoryRedisStockCacheService, never()).cacheAvailableQty(anyLong(), any());
        verify(inventoryInboxRepository).markProcessed("evt-optimistic-fail", "inventory-command-consumer");
        verify(inventoryInboxRepository, never()).markFailed(anyString(), anyString(), anyString(), anyString());
    }

    private InventoryCommandService newService(String hotSkuRaw, int optimisticRetryCount) {
        return new InventoryCommandService(
                inventoryStockJpaRepository,
                inventoryReservationJpaRepository,
                inventoryInboxRepository,
                inventoryDomainEventPublisher,
                inventoryHotSkuLockService,
                inventoryRedisStockCacheService,
                inventoryKafkaFlowMetrics,
                entityManager,
                "wearhouse.inventory.event.v1",
                15,
                optimisticRetryCount,
                200,
                hotSkuRaw
        );
    }

    private Map<String, Object> reservePayload(Long orderId, String orderNo, Long skuId, Integer quantity) {
        Map<String, Object> line = new LinkedHashMap<>();
        line.put("productId", skuId);
        line.put("optionId", skuId);
        line.put("sellerId", 777L);
        line.put("quantity", quantity);

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(line);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId);
        payload.put("orderNo", orderNo);
        payload.put("items", items);
        return payload;
    }
}
