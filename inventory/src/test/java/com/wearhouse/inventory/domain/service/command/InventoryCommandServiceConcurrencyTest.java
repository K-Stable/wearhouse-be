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
import com.wearhouse.inventory.domain.repository.InventoryInboxRepository;
import com.wearhouse.inventory.domain.repository.InventoryReservationRepository;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import com.wearhouse.inventory.infra.product.InventoryProductStatusClient;
import com.wearhouse.inventory.infra.redis.InventoryHotSkuLockService;
import com.wearhouse.inventory.infra.redis.InventoryRedisStockCacheService;
import com.wearhouse.inventory.support.monitoring.InventoryKafkaFlowMetrics;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class InventoryCommandServiceConcurrencyTest {

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
        verify(inventoryStockRepository, never()).findBySkuId(anyLong());
    }

    @Test
    void 낙관락_재시도_소진시_예약실패_이벤트를_발행한다() {
        InventoryCommandService service = newService("", 3);
        Map<String, Object> payload = reservePayload(2L, "ORDER-2", 201L, 1);

        when(inventoryInboxRepository.tryReceive(anyString(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(true);
        when(inventoryStockRepository.findBySkuId(eq(201L)))
                .thenAnswer(invocation -> Optional.of(InventoryStockEntity.create(
                        201L,
                        10,
                        777L,
                        9001L,
                        "Debug Product",
                        BigDecimal.valueOf(50000),
                        "OUTER",
                        "S",
                        "Black",
                        "https://cdn.example.com/main.jpg"
                )));
        when(inventoryStockRepository.saveAndFlush(any(InventoryStockEntity.class)))
                .thenThrow(new ObjectOptimisticLockingFailureException(InventoryStockEntity.class, 201L));

        service.onReserveRequested("evt-optimistic-fail", "inventory-command", "2", "{}", payload);

        ArgumentCaptor<InventoryDomainEvent> eventCaptor = ArgumentCaptor.forClass(InventoryDomainEvent.class);
        verify(inventoryDomainEventPublisher).publish(eventCaptor.capture());

        Map<String, Object> envelope = eventCaptor.getValue().toEnvelope();
        @SuppressWarnings("unchecked")
        Map<String, Object> eventPayload = (Map<String, Object>) envelope.get("payload");

        assertThat(envelope.get("eventType")).isEqualTo("StockReserveFailed");
        assertThat(eventPayload.get("reasonCode")).isEqualTo("INVENTORY_409_002");

        verify(inventoryStockRepository, times(3)).findBySkuId(201L);
        verify(inventoryStockRepository, times(3)).saveAndFlush(any(InventoryStockEntity.class));
        verify(entityManager, times(2)).clear();
        verify(inventoryRedisStockCacheService, never()).cacheAvailableQty(anyLong(), any());
        verify(inventoryInboxRepository).markProcessed("evt-optimistic-fail", "inventory-command-consumer");
        verify(inventoryInboxRepository, never()).markFailed(anyString(), anyString(), anyString(), anyString());
    }

    private InventoryCommandService newService(String hotSkuRaw, int optimisticRetryCount) {
        InventoryCommandService service = new InventoryCommandService(
                inventoryStockRepository,
                inventoryReservationRepository,
                inventoryInboxRepository,
                inventoryDomainEventPublisher,
                inventoryHotSkuLockService,
                inventoryRedisStockCacheService,
                inventoryProductStatusClient,
                inventoryKafkaFlowMetrics,
                entityManager
        );
        ReflectionTestUtils.setField(service, "inventoryEventTopic", "wearhouse.inventory.event.v1");
        ReflectionTestUtils.setField(service, "reservationHoldMinutes", 15);
        ReflectionTestUtils.setField(service, "optimisticRetryCount", optimisticRetryCount);
        ReflectionTestUtils.setField(service, "reservationExpireBatchSize", 200);
        ReflectionTestUtils.setField(service, "hotSkuRaw", hotSkuRaw);
        ReflectionTestUtils.invokeMethod(service, "initializeHotSkuIds");
        return service;
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
