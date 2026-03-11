package com.wearhouse.inventory.infra.redis;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryHotSkuLockService {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = unlockScript();

    private final StringRedisTemplate stringRedisTemplate;
    @Value("${wearhouse.inventory.lock.wait-time-ms:1200}")
    private long waitTimeMs;
    @Value("${wearhouse.inventory.lock.lease-time-ms:3000}")
    private long leaseTimeMs;
    @Value("${wearhouse.inventory.lock.retry-interval-ms:40}")
    private long retryIntervalMs;
    @Value("${wearhouse.inventory.lock.key-prefix:inventory:lock:sku:}")
    private String lockKeyPrefix;

    public SkuLockHandle acquire(Long skuId, String ownerToken) {
        String key = lockKey(skuId);
        long deadlineAt = System.currentTimeMillis() + waitTimeMs;

        while (System.currentTimeMillis() <= deadlineAt) {
            Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(
                    key,
                    ownerToken,
                    Duration.ofMillis(leaseTimeMs)
            );
            if (Boolean.TRUE.equals(acquired)) {
                return new SkuLockHandle(key, ownerToken);
            }
            sleepQuietly();
        }
        return null;
    }

    public void release(SkuLockHandle handle) {
        if (handle == null) {
            return;
        }
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                java.util.List.of(handle.key()),
                handle.ownerToken()
        );
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(retryIntervalMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private String lockKey(Long skuId) {
        return lockKeyPrefix + skuId;
    }

    private static DefaultRedisScript<Long> unlockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                """
                        if redis.call('GET', KEYS[1]) == ARGV[1] then
                            return redis.call('DEL', KEYS[1])
                        end
                        return 0
                        """
        );
        script.setResultType(Long.class);
        return script;
    }

    public record SkuLockHandle(String key, String ownerToken) {
    }
}
