package com.wearhouse.product.domain.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.product.domain.dto.request.ProductCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.seller.SellerProductCommandService;
import com.wearhouse.product.domain.service.seller.SellerProductQueryService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductSellerControllerTest {

    @Mock
    private SellerProductCommandService sellerProductCommandService;

    @Mock
    private SellerProductQueryService sellerProductQueryService;

    @InjectMocks
    private ProductSellerController productSellerController;

    @Test
    void createProductShouldReturnWrappedResponseWithoutData() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductCreateRequest request = new ProductCreateRequest(
                "Gray vintage 3D computer",
                new BigDecimal("50000"),
                Category.OUTER,
                "2026 spring collection",
                "https://cdn.example.com/products/main.jpg",
                List.of(
                        "https://cdn.example.com/products/preview-1.jpg",
                        "https://cdn.example.com/products/preview-2.jpg"
                ),
                List.of("https://cdn.example.com/products/detail-1.jpg"),
                ProductStatus.PENDING,
                List.of(
                        new ProductOptionCreateRequest("S", "Black", 10, BigDecimal.ZERO),
                        new ProductOptionCreateRequest("M", "Black", 3, BigDecimal.ZERO),
                        new ProductOptionCreateRequest("S", "Navy", 5, BigDecimal.ZERO)
                )
        );

        ApiResponse<Void> response = productSellerController.createProduct(seller, request);

        verify(sellerProductCommandService).createProduct(seller, request);
        verifyNoInteractions(sellerProductQueryService);
        assertTrue(response.success());
        assertEquals(ProductSuccessCode.PRODUCT_CREATED.code(), response.code());
        assertEquals(ProductSuccessCode.PRODUCT_CREATED.message(), response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }
}
