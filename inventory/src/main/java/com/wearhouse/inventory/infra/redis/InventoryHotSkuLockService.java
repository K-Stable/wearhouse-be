package com.wearhouse.inventory.infra.redis;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryHotSkuLockService {

    private final RedissonClient redissonClient;
    @Value("${wearhouse.inventory.lock.wait-time-ms:1200}")
    private long waitTimeMs;
    @Value("${wearhouse.inventory.lock.lease-time-ms:3000}")
    private long leaseTimeMs;
    @Value("${wearhouse.inventory.lock.key-prefix:inventory:lock:sku:}")
    private String lockKeyPrefix;

    public SkuLockHandle acquire(Long skuId, String ownerToken) {
        String key = lockKey(skuId);
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS);
            if (!acquired) {
                return null;
            }
            return new SkuLockHandle(key, ownerToken, lock);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    public void release(SkuLockHandle handle) {
        if (handle == null) {
            return;
        }
        RLock lock = handle.lock();
        if (lock == null) {
            return;
        }
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }

    private String lockKey(Long skuId) {
        return lockKeyPrefix + skuId;
    }

    public record SkuLockHandle(String key, String ownerToken, RLock lock) {
    }
}
