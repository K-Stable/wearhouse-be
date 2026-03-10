package com.wearhouse.inventory.domain.service.query;

import com.wearhouse.common.global.error.CommonErrorCode;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.ReadTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.inventory.domain.dto.response.SellerInventoryItemResponse;
import com.wearhouse.inventory.infra.jpa.repository.InventoryStockJpaRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class InventorySellerQueryService {

    private final InventoryStockJpaRepository inventoryStockJpaRepository;

    public InventorySellerQueryService(InventoryStockJpaRepository inventoryStockJpaRepository) {
        this.inventoryStockJpaRepository = inventoryStockJpaRepository;
    }

    @ReadTx
    public List<SellerInventoryItemResponse> getSellerInventories(LoginUser currentUser, String keyword, int limit) {
        Long sellerId = requireSeller(currentUser);
        String normalizedKeyword = normalize(keyword);
        int normalizedLimit = normalizeLimit(limit, 200);

        return inventoryStockJpaRepository.findSellerStocks(sellerId, normalizedKeyword).stream()
                .limit(normalizedLimit)
                .map(SellerInventoryItemResponse::from)
                .toList();
    }

    private Long requireSeller(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !currentUser.isSeller()) {
            throw new ErrorException(CommonErrorCode.FORBIDDEN);
        }
        return currentUser.userId();
    }

    private String normalize(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        return keyword.trim();
    }

    private int normalizeLimit(int value, int max) {
        if (value <= 0) {
            return 50;
        }
        return Math.min(value, max);
    }
}
