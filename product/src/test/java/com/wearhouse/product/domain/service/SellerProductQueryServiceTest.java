package com.wearhouse.product.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.response.BuyerProductDetailResponse;
import com.wearhouse.product.domain.entity.ProductEntity;
import com.wearhouse.product.domain.entity.ProductSeasonEntity;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.repository.ProductRepository;
import com.wearhouse.product.domain.repository.ProductSeasonRepository;
import com.wearhouse.product.domain.service.seller.SellerProductQueryService;
import com.wearhouse.product.infra.inventory.ProductInventoryClient;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SellerProductQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductSeasonRepository productSeasonRepository;

    @Mock
    private ProductInventoryClient productInventoryClient;

    @InjectMocks
    private SellerProductQueryService sellerProductQueryService;

    @Test
    void getSellerProductsShouldValidateSeasonOwnershipWhenSeasonIdProvided() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        when(productSeasonRepository.findByIdAndSellerId(7L, 11L))
                .thenReturn(Optional.of(ProductSeasonEntity.create(11L, "2026 SUMMER")));
        when(productRepository.findSellerProducts(11L, null, null, null, 7L, PageRequest.of(0, 21)))
                .thenReturn(List.of());

        sellerProductQueryService.getSellerProducts(seller, null, null, null, 20, 7L);

        verify(productSeasonRepository).findByIdAndSellerId(7L, 11L);
        verify(productRepository).findSellerProducts(11L, null, null, null, 7L, PageRequest.of(0, 21));
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
        when(productInventoryClient.getAvailableQty(1001L)).thenReturn(7);

        BuyerProductDetailResponse response = sellerProductQueryService.getBuyerProductDetail(501L);

        assertEquals(7, response.options().get(0).stockQuantity());
        verify(productInventoryClient).getAvailableQty(1001L);
    }
}
