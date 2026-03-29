package com.wearhouse.product.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.model.BuyerProductSortType;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.buyer.BuyerProductQueryService;
import com.wearhouse.product.domain.service.buyer.BuyerProductResponseMapper;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BuyerProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSeasonRepository productSeasonRepository;

    @Mock
    private ProductInventoryClient productInventoryClient;

    @Mock
    private S3StorageService s3StorageService;

    private BuyerProductQueryService buyerProductQueryService;

    @BeforeEach
    void setUp() {
        ProductImageUrlResolver productImageUrlResolver = new ProductImageUrlResolver(s3StorageService);
        ProductStockResolver productStockResolver = new ProductStockResolver(productInventoryClient);
        BuyerProductResponseMapper buyerProductResponseMapper = new BuyerProductResponseMapper(productImageUrlResolver);
        buyerProductQueryService = new BuyerProductQueryService(
                productRepository,
                productSeasonRepository,
                productStockResolver,
                buyerProductResponseMapper
        );
    }

    @Test
    void getBuyerProductDetailShouldIncludeOptionStockQuantity() {
        ProductEntity product = ProductEntity.create(
                11L,
                "Debug Product",
                new BigDecimal("50000"),
                Category.OUTER,
                "desc",
                "size-guide",
                "shipping",
                ProductStatus.RELEASED
        );
        ReflectionTestUtils.setField(product, "id", 501L);
        product.addOption("M", "Black", 10, 0);
        ReflectionTestUtils.setField(product.getOptions().get(0), "id", 1001L);

        when(productRepository.findByIdAndStatus(501L, ProductStatus.RELEASED))
                .thenReturn(Optional.of(product));
        when(productRepository.findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(
                ProductStatus.RELEASED,
                Category.OUTER,
                501L
        )).thenReturn(List.of());
        when(productInventoryClient.getAvailableQtyBulk(List.of(1001L))).thenReturn(Map.of(1001L, 7));

        BuyerProductDetailResponse response = buyerProductQueryService.getBuyerProductDetail(501L);

        assertEquals(7, response.options().get(0).stockQuantity());
        verify(productInventoryClient).getAvailableQtyBulk(List.of(1001L));
    }

    @Test
    void getBuyerProductsShouldUseLatestSortByDefault() {
        when(productRepository.findBuyerProductsByCursor(
                null,
                null,
                BuyerProductSortType.LATEST,
                21
        )).thenReturn(List.of());

        buyerProductQueryService.getBuyerProducts(null, null, null, 20);

        verify(productRepository).findBuyerProductsByCursor(
                null,
                null,
                BuyerProductSortType.LATEST,
                21
        );
    }

    @Test
    void getBuyerProductsShouldApplySortAndCursorId() {
        when(productRepository.findBuyerProductsByCursor(
                Category.OUTER,
                300L,
                BuyerProductSortType.PRICE_HIGH,
                21
        )).thenReturn(List.of());

        buyerProductQueryService.getBuyerProducts(Category.OUTER, BuyerProductSortType.PRICE_HIGH, 300L, 20);

        verify(productRepository).findBuyerProductsByCursor(
                Category.OUTER,
                300L,
                BuyerProductSortType.PRICE_HIGH,
                21
        );
    }

    @Test
    void getBuyerProductDetailShouldResolveMainImageKeyToUrl() {
        ProductEntity product = ProductEntity.create(
                11L,
                "Debug Product",
                new BigDecimal("50000"),
                Category.OUTER,
                "desc",
                "size-guide",
                "shipping",
                ProductStatus.RELEASED
        );
        ReflectionTestUtils.setField(product, "id", 501L);
        product.addImage(ProductImageType.MAIN, "prod/products/seller-11/main/a.jpg", 0);

        when(productRepository.findByIdAndStatus(501L, ProductStatus.RELEASED))
                .thenReturn(Optional.of(product));
        when(productRepository.findTop8ByStatusAndCategoryAndIdNotOrderByIdDesc(
                ProductStatus.RELEASED,
                Category.OUTER,
                501L
        )).thenReturn(List.of());
        when(s3StorageService.getImageUrl("prod/products/seller-11/main/a.jpg"))
                .thenReturn("https://cdn.example.com/prod/products/seller-11/main/a.jpg");

        BuyerProductDetailResponse response = buyerProductQueryService.getBuyerProductDetail(501L);

        assertEquals("https://cdn.example.com/prod/products/seller-11/main/a.jpg", response.mainImageUrl());
    }
}
