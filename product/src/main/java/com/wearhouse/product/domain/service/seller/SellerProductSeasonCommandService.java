package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.transactional.WriteTx;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductSeasonCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonUpdateRequest;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerProductSeasonCommandService {

    private final ProductRepository productRepository;
    private final ProductSeasonRepository productSeasonRepository;
    private final SellerProductAccessValidator sellerProductAccessValidator;

    @WriteTx
    public void createProductSeason(LoginUser currentUser, ProductSeasonCreateRequest request) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductSeasonEntity productSeason = ProductSeasonEntity.create(sellerId, normalizeSeasonName(request.name()));
        productSeasonRepository.save(productSeason);
    }

    @WriteTx
    public void updateProductSeason(LoginUser currentUser, Long seasonId, ProductSeasonUpdateRequest request) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductSeasonEntity season = sellerProductAccessValidator.requireSeason(sellerId, seasonId);
        season.updateName(normalizeSeasonName(request.name()));
    }

    @WriteTx
    public void deleteProductSeason(LoginUser currentUser, Long seasonId) {
        Long sellerId = sellerProductAccessValidator.requireSellerId(currentUser);
        ProductSeasonEntity season = sellerProductAccessValidator.requireSeason(sellerId, seasonId);
        if (productRepository.existsByProductSeason_IdAndSellerId(seasonId, sellerId)) {
            throw new ErrorException(ProductErrorCode.PRODUCT_SEASON_IN_USE);
        }
        productSeasonRepository.delete(season);
    }

    private String normalizeSeasonName(String name) {
        return name == null ? null : name.trim();
    }
}
