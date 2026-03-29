package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonUpdateRequest;
import com.wearhouse.product.domain.model.ProductStatus;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SellerProductCommandService {

    private final SellerProductSeasonCommandService sellerProductSeasonCommandService;
    private final SellerProductWriteService sellerProductWriteService;

    public void createProductSeason(LoginUser currentUser, ProductSeasonCreateRequest request) {
        sellerProductSeasonCommandService.createProductSeason(currentUser, request);
    }

    public void updateProductSeason(LoginUser currentUser, Long seasonId, ProductSeasonUpdateRequest request) {
        sellerProductSeasonCommandService.updateProductSeason(currentUser, seasonId, request);
    }

    public void deleteProductSeason(LoginUser currentUser, Long seasonId) {
        sellerProductSeasonCommandService.deleteProductSeason(currentUser, seasonId);
    }

    public void createProduct(LoginUser currentUser, Long seasonId, ProductCreateRequest request) {
        sellerProductWriteService.createProduct(currentUser, seasonId, request);
    }

    public void updateProductStatus(LoginUser currentUser, Long productId, ProductStatus status) {
        sellerProductWriteService.updateProductStatus(currentUser, productId, status);
    }

    public void updateProductStatuses(LoginUser currentUser, List<Long> productIds, ProductStatus status) {
        sellerProductWriteService.updateProductStatuses(currentUser, productIds, status);
    }

    public void deleteProduct(LoginUser currentUser, Long productId) {
        sellerProductWriteService.deleteProduct(currentUser, productId);
    }

    public void markProductsSoldOut(List<Long> productIds) {
        sellerProductWriteService.markProductsSoldOut(productIds);
    }
}
