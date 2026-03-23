package com.wearhouse.inventory.domain.service.seller.query;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.response.InventoryStockResponse;
import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.domain.entity.InventoryStockEntity;
import com.wearhouse.inventory.domain.exception.InventoryErrorCode;
import com.wearhouse.inventory.domain.model.InventoryProductStatus;
import com.wearhouse.inventory.domain.repository.InventoryStockRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerInventoryQueryService {

    private static final int DEFAULT_SELLER_LIMIT = 50;
    private static final int MAX_SELLER_LIMIT = 200;

    private final InventoryStockRepository inventoryStockRepository;

    @ReadTx
    public InventoryStockResponse findStockBySkuId(Long skuId) {
        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
        return InventoryStockResponse.from(stock);
    }

    @ReadTx
    public InventoryStockResponse findSellerInventoryDetail(LoginUser currentUser, Long skuId) {
        Long sellerId = requireSeller(currentUser);
        InventoryStockEntity stock = inventoryStockRepository.findBySkuId(skuId)
                .orElseThrow(() -> new ErrorException(InventoryErrorCode.STOCK_NOT_FOUND));
        if (!sellerId.equals(stock.getSellerId())) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return InventoryStockResponse.from(stock);
    }

    @ReadTx
    public List<SellerInventoryItemResponse> findSellerInventoryItems(
            LoginUser currentUser,
            String keyword,
            String status,
            int limit
    ) {
        Long sellerId = requireSeller(currentUser);
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedStatus = normalizeStatus(status);
        int normalizedLimit = normalizeLimit(limit);

        return inventoryStockRepository.findSellerInventoryItems(
                sellerId,
                normalizedKeyword,
                normalizedStatus,
                PageRequest.of(0, normalizedLimit)
        );
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !currentUser.isSeller()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return currentUser.userId();
    }

    private String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SELLER_LIMIT;
        }
        return Math.min(limit, MAX_SELLER_LIMIT);
    }

    private String normalizeStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        if (InventoryProductStatus.isAll(status)) {
            return null;
        }
        String normalized = InventoryProductStatus.normalizeForFilter(status);
        if (normalized == null) {
            throw new ErrorException(InventoryErrorCode.INVALID_COMMAND);
        }
        return normalized;
    }
}
