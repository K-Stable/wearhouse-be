package com.wearhouse.product.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import com.wearhouse.product.domain.service.common.ProductStockResolver;
import com.wearhouse.product.domain.service.seller.SellerProductAccessValidator;
import com.wearhouse.product.domain.service.seller.SellerProductQueryService;
import com.wearhouse.product.domain.service.seller.SellerProductResponseMapper;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSeasonRepository productSeasonRepository;

    @Mock
    private ProductInventoryClient productInventoryClient;

    @Mock
    private S3StorageService s3StorageService;

    private SellerProductQueryService sellerProductQueryService;

    @BeforeEach
    void setUp() {
        SellerProductAccessValidator accessValidator =
                new SellerProductAccessValidator(productRepository, productSeasonRepository);
        ProductImageUrlResolver productImageUrlResolver = new ProductImageUrlResolver(s3StorageService);
        ProductStockResolver productStockResolver = new ProductStockResolver(productInventoryClient);
        SellerProductResponseMapper sellerProductResponseMapper = new SellerProductResponseMapper(productImageUrlResolver);
        sellerProductQueryService = new SellerProductQueryService(
                productRepository,
                productSeasonRepository,
                accessValidator,
                productStockResolver,
                sellerProductResponseMapper
        );
    }

    @Test
    void getSellerProductsShouldValidateSeasonOwnershipWhenSeasonIdProvided() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        when(productSeasonRepository.findByIdAndSellerId(7L, 11L))
                .thenReturn(Optional.of(ProductSeasonEntity.create(11L, "2026 SUMMER")));
        when(productRepository.findSellerProductsByCursor(11L, null, null, null, 7L, 21))
                .thenReturn(List.of());

        sellerProductQueryService.getSellerProducts(seller, null, null, null, 20, 7L);

        verify(productSeasonRepository).findByIdAndSellerId(7L, 11L);
        verify(productRepository).findSellerProductsByCursor(11L, null, null, null, 7L, 21);
    }

    @Test
    void getSellerProductsShouldThrowWhenSeasonNotOwnedBySeller() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        when(productSeasonRepository.findByIdAndSellerId(999L, 11L)).thenReturn(Optional.empty());

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> sellerProductQueryService.getSellerProducts(seller, ProductStatus.PENDING, null, null, 20, 999L)
        );

        assertEquals(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND, exception.errorCode());
    }
}
