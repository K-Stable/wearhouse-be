package com.wearhouse.inventory.infra.redis;

import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.infra.jpa.repository.InventoryStockJpaRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class InventoryRedisStockCacheService {

    private static final DefaultRedisScript<Long> AVAILABILITY_CHECK_SCRIPT = availabilityCheckScript();

    private final StringRedisTemplate stringRedisTemplate;
    private final InventoryStockJpaRepository inventoryStockJpaRepository;
    private final Duration stockCacheTtl;
    private final String stockKeyPrefix;

    public InventoryRedisStockCacheService(
            StringRedisTemplate stringRedisTemplate,
            InventoryStockJpaRepository inventoryStockJpaRepository,
            @Value("${wearhouse.inventory.cache.stock-ttl-seconds:30}") long stockCacheTtlSeconds,
            @Value("${wearhouse.inventory.cache.stock-key-prefix:inventory:stock:available:}") String stockKeyPrefix
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.inventoryStockJpaRepository = inventoryStockJpaRepository;
        this.stockCacheTtl = Duration.ofSeconds(stockCacheTtlSeconds);
        this.stockKeyPrefix = stockKeyPrefix;
    }

    public AtomicAvailabilityCheckResult checkAvailabilityAtomically(Map<Long, Integer> requestedBySku) {
        if (requestedBySku == null || requestedBySku.isEmpty()) {
            return new AtomicAvailabilityCheckResult(true, Map.of());
        }

        List<Long> orderedSkuIds = requestedBySku.keySet().stream().sorted().toList();
        List<String> keys = toKeys(orderedSkuIds);
        List<String> args = orderedSkuIds.stream()
                .map(skuId -> String.valueOf(requestedBySku.getOrDefault(skuId, 0)))
                .toList();

        warmCacheIfMissing(orderedSkuIds);

        Long evaluation = executeCheck(keys, args);
        if (evaluation != null && evaluation < 0) {
            warmCacheIfMissing(orderedSkuIds);
            evaluation = executeCheck(keys, args);
        }

        Map<Long, Integer> availableBySku = readAvailableBySku(orderedSkuIds);
        return new AtomicAvailabilityCheckResult(Long.valueOf(1L).equals(evaluation), availableBySku);
    }

    public void cacheAvailableQty(Long skuId, Integer availableQty) {
        if (skuId == null || availableQty == null) {
            return;
        }
        stringRedisTemplate.opsForValue().set(stockKey(skuId), String.valueOf(Math.max(availableQty, 0)), stockCacheTtl);
    }

    private void warmCacheIfMissing(List<Long> skuIds) {
        if (skuIds.isEmpty()) {
            return;
        }
        List<String> keys = toKeys(skuIds);
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

        Set<Long> missingSkuIds = collectMissingSkuIds(skuIds, values);
        if (missingSkuIds.isEmpty()) {
            return;
        }

        Map<Long, Integer> availableBySku = inventoryStockJpaRepository.findAllBySkuIdIn(missingSkuIds).stream()
                .collect(Collectors.toMap(InventoryStockEntity::getSkuId, InventoryStockEntity::getAvailableQty));

        for (Long skuId : missingSkuIds) {
            Integer availableQty = availableBySku.getOrDefault(skuId, 0);
            cacheAvailableQty(skuId, availableQty);
        }
    }

    private Set<Long> collectMissingSkuIds(List<Long> skuIds, List<String> values) {
        Set<Long> missingSkuIds = skuIds.stream().collect(Collectors.toSet());
        if (values == null) {
            return missingSkuIds;
        }
        for (int index = 0; index < skuIds.size(); index++) {
            if (index < values.size() && values.get(index) != null) {
                missingSkuIds.remove(skuIds.get(index));
            }
        }
        return missingSkuIds;
    }

    private Long executeCheck(List<String> keys, List<String> args) {
        return stringRedisTemplate.execute(
                AVAILABILITY_CHECK_SCRIPT,
                keys,
                args.toArray()
        );
    }

    private Map<Long, Integer> readAvailableBySku(List<Long> skuIds) {
        List<String> keys = toKeys(skuIds);
        List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);
        Map<Long, Integer> availableBySku = new LinkedHashMap<>();

        for (int index = 0; index < skuIds.size(); index++) {
            String value = values == null || index >= values.size() ? null : values.get(index);
            availableBySku.put(skuIds.get(index), parseAvailableQty(value));
        }
        return availableBySku;
    }

    private int parseAvailableQty(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private List<String> toKeys(Collection<Long> skuIds) {
        List<String> keys = new ArrayList<>();
        for (Long skuId : skuIds) {
            keys.add(stockKey(skuId));
        }
        return keys;
    }

    private String stockKey(Long skuId) {
        return stockKeyPrefix + skuId;
    }

    private static DefaultRedisScript<Long> availabilityCheckScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(
                """
                        for i = 1, #KEYS do
                            local current = redis.call('GET', KEYS[i])
                            if (not current) then
                                return -i
                            end
                            if (tonumber(current) < tonumber(ARGV[i])) then
                                return 0
                            end
                        end
                        return 1
                        """
        );
        script.setResultType(Long.class);
        return script;
    }

    public record AtomicAvailabilityCheckResult(boolean available, Map<Long, Integer> availableBySku) {
    }
}
