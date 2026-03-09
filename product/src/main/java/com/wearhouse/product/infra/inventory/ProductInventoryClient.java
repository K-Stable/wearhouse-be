package com.wearhouse.product.infra.inventory;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.passport.PassportHeaders;
import com.wearhouse.product.infra.feign.InventoryStockFeignClient;
import com.wearhouse.product.infra.feign.dto.InventoryStockResponse;
import com.wearhouse.product.infra.feign.dto.InventoryStockUpsertRequest;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class ProductInventoryClient {

    private final InventoryStockFeignClient inventoryStockFeignClient;

    public ProductInventoryClient(InventoryStockFeignClient inventoryStockFeignClient) {
        this.inventoryStockFeignClient = inventoryStockFeignClient;
    }

    public void upsertStock(Long skuId, Integer availableQty) {
        PassportHeaderBundle headers = resolvePassportHeaders();
        try {
            ApiResponse<InventoryStockResponse> response = inventoryStockFeignClient.upsertStock(
                    headers.encodedUser(),
                    headers.signature(),
                    headers.timestamp(),
                    new InventoryStockUpsertRequest(skuId, availableQty)
            );
            if (response == null || !response.success()) {
                throw new IllegalStateException("inventory stock upsert 응답이 유효하지 않습니다.");
            }
        } catch (FeignException exception) {
            throw new IllegalStateException("inventory stock upsert 호출 실패: " + exception.status(), exception);
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
