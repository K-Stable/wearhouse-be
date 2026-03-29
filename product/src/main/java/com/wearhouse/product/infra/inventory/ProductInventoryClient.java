package com.wearhouse.product.infra.inventory;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryAvailabilityCheckRequest;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryAvailabilityCheckResponse;
import com.wearhouse.common.infra.feign.inventory.InventoryStockFeignClient;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryStockResponse;
import com.wearhouse.common.infra.feign.inventory.dto.InventoryStockUpsertRequest;
import com.wearhouse.common.security.passport.PassportHeaders;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
@RequiredArgsConstructor
public class ProductInventoryClient {

    private final InventoryStockFeignClient inventoryStockFeignClient;


    public void upsertStock(
            Long skuId,
            Integer availableQty,
            String productStatus,
            Long sellerId,
            Long productId,
            String productName,
            BigDecimal productPrice,
            String category,
            String size,
            String color,
            String mainImageUrl
    ) {
        PassportHeaderBundle headers = resolvePassportHeaders();
        try {
            ApiResponse<InventoryStockResponse> response = inventoryStockFeignClient.upsertStock(
                    headers.encodedUser(),
                    headers.signature(),
                    headers.timestamp(),
                    new InventoryStockUpsertRequest(
                            skuId,
                            availableQty,
                            sellerId,
                            productId,
                            productName,
                            productPrice,
                            category,
                            productStatus,
                            size,
                            color,
                            mainImageUrl
                    )
            );
            if (response == null || !response.success()) {
                throw new IllegalStateException("inventory upsert 응답이 유효하지 않습니다.");
            }
        } catch (FeignException exception) {
            throw new IllegalStateException("inventory upsert 호출 실패: " + exception.status(), exception);
        }
    }

    public Integer getAvailableQty(Long skuId) {
        PassportHeaderBundle headers = resolvePassportHeaders();
        try {
            ApiResponse<InventoryStockResponse> response = inventoryStockFeignClient.getStock(
                    headers.encodedUser(),
                    headers.signature(),
                    headers.timestamp(),
                    skuId
            );
            if (response == null || !response.success() || response.data() == null) {
                throw new IllegalStateException("inventory stock 조회 응답이 유효하지 않습니다.");
            }
            return response.data().availableQty();
        } catch (FeignException exception) {
            throw new IllegalStateException("inventory stock 조회 호출 실패: " + exception.status(), exception);
        }
    }

    public Map<Long, Integer> getAvailableQtyBulk(List<Long> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return Map.of();
        }

        List<Long> distinctSkuIds = skuIds.stream()
                .filter(skuId -> skuId != null)
                .distinct()
                .toList();
        if (distinctSkuIds.isEmpty()) {
            return Map.of();
        }

        PassportHeaderBundle headers = resolvePassportHeaders();
        List<InventoryAvailabilityCheckRequest.InventoryAvailabilityLineRequest> items = distinctSkuIds.stream()
                .map(skuId -> new InventoryAvailabilityCheckRequest.InventoryAvailabilityLineRequest(
                        skuId,
                        null,
                        1
                ))
                .toList();

        try {
            InventoryAvailabilityCheckResponse response = inventoryStockFeignClient.checkAvailability(
                    headers.encodedUser(),
                    headers.signature(),
                    headers.timestamp(),
                    new InventoryAvailabilityCheckRequest(items)
            );
            if (response == null || response.items() == null) {
                throw new IllegalStateException("inventory bulk stock 조회 응답이 유효하지 않습니다.");
            }
            Map<Long, Integer> availableBySku = new LinkedHashMap<>();
            for (InventoryAvailabilityCheckResponse.InventoryAvailabilityLineResponse line : response.items()) {
                if (line == null || line.skuId() == null) {
                    continue;
                }
                availableBySku.put(line.skuId(), line.availableQty());
            }
            return availableBySku;
        } catch (FeignException exception) {
            String skuSummary = distinctSkuIds.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
            throw new IllegalStateException(
                    "inventory bulk stock 조회 호출 실패: " + exception.status() + ", skuIds=" + skuSummary,
                    exception
            );
        }
    }

    public void deleteProductStocks(Long productId) {
        PassportHeaderBundle headers = resolvePassportHeaders();
        try {
            ApiResponse<Void> response = inventoryStockFeignClient.deleteStocksByProductId(
                    headers.encodedUser(),
                    headers.signature(),
                    headers.timestamp(),
                    productId
            );
            if (response == null || !response.success()) {
                throw new IllegalStateException("inventory product stock delete 응답이 유효하지 않습니다.");
            }
        } catch (FeignException exception) {
            throw new IllegalStateException("inventory product stock delete 호출 실패: " + exception.status(), exception);
        }
    }

    private PassportHeaderBundle resolvePassportHeaders() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            throw new IllegalStateException("inventory 호출에 필요한 request context가 없습니다.");
        }
        HttpServletRequest request = attributes.getRequest();
        String encodedUser = request.getHeader(PassportHeaders.USER);
        String signature = request.getHeader(PassportHeaders.SIGNATURE);
        String timestamp = request.getHeader(PassportHeaders.TIMESTAMP);
        if (encodedUser == null || signature == null || timestamp == null) {
            throw new IllegalStateException("inventory 호출에 필요한 passport 헤더가 없습니다.");
        }
        return new PassportHeaderBundle(encodedUser, signature, timestamp);
    }

    private record PassportHeaderBundle(
            String encodedUser,
            String signature,
            String timestamp
    ) {
    }
}
