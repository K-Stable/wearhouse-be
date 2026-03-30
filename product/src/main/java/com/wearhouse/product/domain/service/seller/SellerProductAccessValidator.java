package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SellerProductAccessValidator {

    private final ProductRepository productRepository;
    private final ProductSeasonRepository productSeasonRepository;

    public Long requireSellerId(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !currentUser.isSeller()) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    public ProductSeasonEntity requireSeason(Long sellerId, Long seasonId) {
        return productSeasonRepository.findByIdAndSellerId(seasonId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND));
    }

    public void validateSeasonOwnership(Long sellerId, Long seasonId) {
        if (seasonId == null) {
            return;
        }
        requireSeason(sellerId, seasonId);
    }

    public ProductEntity requireProduct(Long sellerId, Long productId) {
        return productRepository.findByIdAndSellerId(productId, sellerId)
                .orElseThrow(() -> new ErrorException(ProductErrorCode.PRODUCT_NOT_FOUND));
    }
}
