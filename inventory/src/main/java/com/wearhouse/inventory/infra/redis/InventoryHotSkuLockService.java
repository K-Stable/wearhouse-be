package com.wearhouse.inventory.infra.redis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import com.wearhouse.inventory.support.config.InventoryProperties;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class InventoryHotSkuLockService {

    private final RedissonClient redissonClient;
    private final InventoryProperties inventoryProperties;

    public <T> T withHotSkuLocks(
            Set<Long> skuIds,
            String ownerToken,
            Supplier<T> action
    ) {
        // deadlock 회피를 위해 정렬된 순서로 락을 획득하고, 실행 후 역순 해제한다.
        List<SkuLockHandle> lockHandles = acquireAll(skuIds, ownerToken);
        try {
            return action.get();
        } finally {
            // 트랜잭션 경계와 맞춰 락을 해제한다.
            releaseAfterTransaction(lockHandles);
        }
    }

    public SkuLockHandle acquire(Long skuId, String ownerToken) {
        String key = lockKey(skuId);
        RLock lock = redissonClient.getLock(key);
        try {
            boolean acquired = lock.tryLock(
                    inventoryProperties.lock().waitTimeMs(),
                    inventoryProperties.lock().leaseTimeMs(),
                    TimeUnit.MILLISECONDS
            );
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
        return inventoryProperties.lock().keyPrefix() + skuId;
    }

    private List<SkuLockHandle> acquireAll(Set<Long> skuIds, String ownerToken) {
        List<Long> lockTargets = selectLockTargets(skuIds);
        if (lockTargets.isEmpty()) {
            return List.of();
        }

        List<SkuLockHandle> lockHandles = new ArrayList<>();
        try {
            for (Long skuId : lockTargets) {
                SkuLockHandle handle = acquire(skuId, ownerToken);
                if (handle == null) {
                    throw new LockAcquireException("락 획득 실패");
                }
                lockHandles.add(handle);
            }
            return lockHandles;
        } catch (RuntimeException exception) {
            releaseNow(lockHandles);
            throw exception;
        }
    }

    private List<Long> selectLockTargets(Set<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return List.of();
        }
        // hot SKU만 락 대상으로 제한해 락 오버헤드를 줄인다.
        Set<Long> hotSkuIds = parseHotSkuIds(inventoryProperties.hotSkus());
        if (hotSkuIds.isEmpty()) {
            return skuIds.stream().sorted().toList();
        }
        return skuIds.stream()
                .filter(hotSkuIds::contains)
                .sorted()
                .toList();
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

    private void releaseAfterTransaction(List<SkuLockHandle> lockHandles) {
        if (lockHandles == null || lockHandles.isEmpty()) {
            return;
        }
        List<SkuLockHandle> releaseTargets = new ArrayList<>(lockHandles);
        if (TransactionSynchronizationManager.isSynchronizationActive()
                && TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    // commit/rollback 후에 락을 풀어 DB 상태와 락 생명주기를 맞춘다.
                    releaseNow(releaseTargets);
                }
            });
            return;
        }
        releaseNow(releaseTargets);
    }

    private void releaseNow(List<SkuLockHandle> lockHandles) {
        if (lockHandles == null || lockHandles.isEmpty()) {
            return;
        }
        List<SkuLockHandle> releaseTargets = new ArrayList<>(lockHandles);
        Collections.reverse(releaseTargets);
        for (SkuLockHandle lockHandle : releaseTargets) {
            release(lockHandle);
        }
    }

    public record SkuLockHandle(String key, String ownerToken, RLock lock) {
    }

    public static class LockAcquireException extends RuntimeException {
        public LockAcquireException(String message) {
            super(message);
        }
    }
}
