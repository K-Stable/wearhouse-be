package com.wearhouse.product.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonUpdateRequest;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductOptionEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.common.ProductImageUrlResolver;
import com.wearhouse.product.domain.service.seller.SellerProductAccessValidator;
import com.wearhouse.product.domain.service.seller.SellerProductCommandService;
import com.wearhouse.product.domain.service.seller.SellerProductInventorySyncService;
import com.wearhouse.product.domain.service.seller.SellerProductSeasonCommandService;
import com.wearhouse.product.domain.service.seller.SellerProductWriteService;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerProductCommandServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSeasonRepository productSeasonRepository;

    @Mock
    private ProductInventoryClient productInventoryClient;

    @Mock
    private S3StorageService s3StorageService;

    @Test
    void createProductShouldFlushBeforeInventorySync() {
        SellerProductCommandService service = createService();
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductCreateRequest request = new ProductCreateRequest(
                "Debug Product",
                new BigDecimal("50000"),
                Category.OUTER,
                "desc",
                "size-guide",
                "shipping",
                List.of(
                        new ProductOptionCreateRequest("S", "Black", 10),
                        new ProductOptionCreateRequest("M", "Black", 3)
                ),
                "prod/products/seller-11/main/main.jpg",
                List.of("prod/products/seller-11/preview/p1.jpg"),
                List.of("prod/products/seller-11/detail/d1.jpg"),
                ProductStatus.PENDING
        );
        when(productSeasonRepository.findByIdAndSellerId(7L, 11L))
                .thenReturn(Optional.of(ProductSeasonEntity.create(11L, "2026 SUMMER")));

        when(productRepository.saveAndFlush(any(ProductEntity.class))).thenAnswer(invocation -> {
            ProductEntity persisted = invocation.getArgument(0);
            ReflectionTestUtils.setField(persisted, "id", 501L);
            List<ProductOptionEntity> options = persisted.getOptions();
            ReflectionTestUtils.setField(options.get(0), "id", 1001L);
            ReflectionTestUtils.setField(options.get(1), "id", 1002L);
            return persisted;
        });
        when(s3StorageService.getImageUrl("prod/products/seller-11/main/main.jpg"))
                .thenReturn("https://cdn.example.com/prod/products/seller-11/main/main.jpg");

        service.createProduct(seller, 7L, request);

        verify(productRepository).saveAndFlush(any(ProductEntity.class));
        verify(productSeasonRepository).findByIdAndSellerId(7L, 11L);
        verify(productInventoryClient).upsertStock(
                eq(1001L),
                eq(10),
                eq("PENDING"),
                eq(11L),
                eq(501L),
                eq("Debug Product"),
                eq(new BigDecimal("50000")),
                eq("OUTER"),
                eq("S"),
                eq("Black"),
                eq("https://cdn.example.com/prod/products/seller-11/main/main.jpg")
        );
        verify(productInventoryClient).upsertStock(
                eq(1002L),
                eq(3),
                eq("PENDING"),
                eq(11L),
                eq(501L),
                eq("Debug Product"),
                eq(new BigDecimal("50000")),
                eq("OUTER"),
                eq("M"),
                eq("Black"),
                eq("https://cdn.example.com/prod/products/seller-11/main/main.jpg")
        );
    }

    @Test
    void createProductShouldThrowWhenSeasonNotOwnedBySeller() {
        SellerProductCommandService service = createService();
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductCreateRequest request = new ProductCreateRequest(
                "Debug Product",
                new BigDecimal("50000"),
                Category.OUTER,
                "desc",
                "size-guide",
                "shipping",
                List.of(new ProductOptionCreateRequest("S", "Black", 10)),
                "prod/products/seller-11/main/main.jpg",
                List.of("prod/products/seller-11/preview/p1.jpg"),
                List.of("prod/products/seller-11/detail/d1.jpg"),
                ProductStatus.PENDING
        );
        when(productSeasonRepository.findByIdAndSellerId(999L, 11L)).thenReturn(Optional.empty());

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> service.createProduct(seller, 999L, request)
        );

        assertEquals(ProductErrorCode.PRODUCT_SEASON_NOT_FOUND, exception.errorCode());
    }

    @Test
    void updateProductSeasonShouldTrimName() {
        SellerProductCommandService service = createService();
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductSeasonEntity season = ProductSeasonEntity.create(11L, "old-season");
        when(productSeasonRepository.findByIdAndSellerId(3L, 11L)).thenReturn(Optional.of(season));

        service.updateProductSeason(seller, 3L, new ProductSeasonUpdateRequest("  2026 SUMMER  "));

        assertEquals("2026 SUMMER", season.getName());
        verify(productSeasonRepository).findByIdAndSellerId(3L, 11L);
    }

    @Test
    void deleteProductSeasonShouldThrowWhenSeasonInUse() {
        SellerProductCommandService service = createService();
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductSeasonEntity season = ProductSeasonEntity.create(11L, "2026 SUMMER");
        when(productSeasonRepository.findByIdAndSellerId(3L, 11L)).thenReturn(Optional.of(season));
        when(productRepository.existsByProductSeason_IdAndSellerId(3L, 11L)).thenReturn(true);

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> service.deleteProductSeason(seller, 3L)
        );

        assertEquals(ProductErrorCode.PRODUCT_SEASON_IN_USE, exception.errorCode());
    }

    @Test
    void deleteProductShouldDeleteInventoryStocksFirst() {
        SellerProductCommandService service = createService();
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
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
        when(productRepository.findByIdAndSellerId(501L, 11L)).thenReturn(Optional.of(product));

        service.deleteProduct(seller, 501L);

        verify(productInventoryClient).deleteProductStocks(501L);
        verify(productRepository).delete(product);
    }

    private SellerProductCommandService createService() {
        SellerProductAccessValidator accessValidator =
                new SellerProductAccessValidator(productRepository, productSeasonRepository);
        SellerProductSeasonCommandService seasonCommandService =
                new SellerProductSeasonCommandService(productRepository, productSeasonRepository, accessValidator);
        ProductImageUrlResolver productImageUrlResolver = new ProductImageUrlResolver(s3StorageService);
        SellerProductInventorySyncService inventorySyncService =
                new SellerProductInventorySyncService(productInventoryClient, productImageUrlResolver);
        SellerProductWriteService sellerProductWriteService =
                new SellerProductWriteService(productRepository, accessValidator, inventorySyncService);
        return new SellerProductCommandService(seasonCommandService, sellerProductWriteService);
    }
}
