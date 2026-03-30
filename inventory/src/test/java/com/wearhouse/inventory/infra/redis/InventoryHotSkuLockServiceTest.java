package com.wearhouse.inventory.infra.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import com.wearhouse.inventory.support.config.InventoryProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

@ExtendWith(MockitoExtension.class)
class InventoryHotSkuLockServiceTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private RLock rLock;

    @Test
    void 락_획득에_성공하면_핸들을_반환한다() throws Exception {
        InventoryHotSkuLockService service = newService();
        when(redissonClient.getLock("inventory:lock:sku:101")).thenReturn(rLock);
        when(rLock.tryLock(1200L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(true);

        InventoryHotSkuLockService.SkuLockHandle handle = service.acquire(101L, "owner-token");

        assertThat(handle).isNotNull();
        assertThat(handle.key()).isEqualTo("inventory:lock:sku:101");
        assertThat(handle.ownerToken()).isEqualTo("owner-token");
    }

    @Test
    void 락_획득에_실패하면_null을_반환한다() throws Exception {
        InventoryHotSkuLockService service = newService();
        when(redissonClient.getLock("inventory:lock:sku:102")).thenReturn(rLock);
        when(rLock.tryLock(1200L, 3000L, TimeUnit.MILLISECONDS)).thenReturn(false);

        InventoryHotSkuLockService.SkuLockHandle handle = service.acquire(102L, "owner-token");

        assertThat(handle).isNull();
    }

    @Test
    void 현재_스레드가_락을_보유하면_release시_unlock한다() {
        InventoryHotSkuLockService service = newService();
        when(rLock.isHeldByCurrentThread()).thenReturn(true);
        InventoryHotSkuLockService.SkuLockHandle handle =
                new InventoryHotSkuLockService.SkuLockHandle("inventory:lock:sku:103", "owner-token", rLock);

        service.release(handle);

        verify(rLock).unlock();
    }

    @Test
    void 현재_스레드가_락을_보유하지_않으면_release시_unlock하지_않는다() {
        InventoryHotSkuLockService service = newService();
        when(rLock.isHeldByCurrentThread()).thenReturn(false);
        InventoryHotSkuLockService.SkuLockHandle handle =
                new InventoryHotSkuLockService.SkuLockHandle("inventory:lock:sku:104", "owner-token", rLock);

        service.release(handle);

        verify(rLock, never()).unlock();
    }

    private InventoryHotSkuLockService newService() {
        InventoryProperties inventoryProperties = new InventoryProperties(
                15,
                3,
                30000L,
                200,
                "",
                new InventoryProperties.Lock(1200L, 3000L, 40L, "inventory:lock:sku:"),
                new InventoryProperties.Cache(30L, "inventory:stock:available:"),
                new InventoryProperties.Internal("wearhouse-inventory-internal-secret")
        );
        return new InventoryHotSkuLockService(redissonClient, inventoryProperties);
    }
}
