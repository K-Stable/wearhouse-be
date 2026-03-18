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
import com.wearhouse.product.domain.dto.request.ProductImagePresignedUploadRequest;
import com.wearhouse.product.domain.dto.request.ProductOptionCreateRequest;
import com.wearhouse.product.domain.dto.request.ProductSeasonUpdateRequest;
import com.wearhouse.product.domain.dto.response.ProductImagePresignedUploadResponse;
import com.wearhouse.product.domain.dto.response.ProductSeasonListResponse;
import com.wearhouse.product.domain.model.Category;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.model.ProductStatus;
import com.wearhouse.product.domain.response.ProductSuccessCode;
import com.wearhouse.product.domain.service.seller.SellerProductCommandService;
import com.wearhouse.product.domain.service.seller.SellerProductImageService;
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

    @Mock
    private SellerProductImageService sellerProductImageService;

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
                "oversized fit",
                "free shipping",
                List.of(
                        new ProductOptionCreateRequest("S", "Black", 10),
                        new ProductOptionCreateRequest("M", "Black", 3),
                        new ProductOptionCreateRequest("S", "Navy", 5)
                ),
                "prod/products/seller-11/main/main.jpg",
                List.of(
                        "prod/products/seller-11/preview/preview-1.jpg",
                        "prod/products/seller-11/preview/preview-2.jpg"
                ),
                List.of("prod/products/seller-11/detail/detail-1.jpg"),
                ProductStatus.PENDING
        );

        ApiResponse<Void> response = productSellerController.createProduct(seller, 7L, request);

        verify(sellerProductCommandService).createProduct(seller, 7L, request);
        verifyNoInteractions(sellerProductQueryService);
        assertTrue(response.success());
        assertEquals(ProductSuccessCode.PRODUCT_CREATED.code(), response.code());
        assertEquals(ProductSuccessCode.PRODUCT_CREATED.message(), response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void updateProductSeasonShouldReturnWrappedResponseWithoutData() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductSeasonUpdateRequest request = new ProductSeasonUpdateRequest("2026 Summer");

        ApiResponse<Void> response = productSellerController.updateProductSeason(seller, 3L, request);

        verify(sellerProductCommandService).updateProductSeason(seller, 3L, request);
        verifyNoInteractions(sellerProductQueryService);
        assertTrue(response.success());
        assertEquals(ProductSuccessCode.PRODUCT_SEASON_UPDATED.code(), response.code());
        assertEquals(ProductSuccessCode.PRODUCT_SEASON_UPDATED.message(), response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void deleteProductSeasonShouldReturnWrappedResponseWithoutData() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);

        ApiResponse<Void> response = productSellerController.deleteProductSeason(seller, 3L);

        verify(sellerProductCommandService).deleteProductSeason(seller, 3L);
        verifyNoInteractions(sellerProductQueryService);
        assertTrue(response.success());
        assertEquals(ProductSuccessCode.PRODUCT_SEASON_DELETED.code(), response.code());
        assertEquals(ProductSuccessCode.PRODUCT_SEASON_DELETED.message(), response.message());
        assertNull(response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void getSellerProductSeasonShouldReturnWrappedResponseWithData() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductSeasonListResponse season = new ProductSeasonListResponse(3L, "2026 Summer");
        org.mockito.Mockito.when(sellerProductQueryService.getSellerSeason(seller, 3L)).thenReturn(season);

        ApiResponse<ProductSeasonListResponse> response = productSellerController.getSellerProductSeason(seller, 3L);

        verify(sellerProductQueryService).getSellerSeason(seller, 3L);
        verifyNoInteractions(sellerProductCommandService);
        assertTrue(response.success());
        assertEquals(ProductSuccessCode.SELLER_PRODUCT_SEASON_FETCHED.code(), response.code());
        assertEquals(ProductSuccessCode.SELLER_PRODUCT_SEASON_FETCHED.message(), response.message());
        assertEquals(season, response.data());
        assertNotNull(response.timestamp());
    }

    @Test
    void createPresignedUploadUrlShouldReturnWrappedResponseWithData() {
        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductImagePresignedUploadRequest request = new ProductImagePresignedUploadRequest(
                "product-main.jpg",
                "image/jpeg",
                ProductImageType.MAIN
        );
        ProductImagePresignedUploadResponse payload = new ProductImagePresignedUploadResponse(
                "prod/products/seller-11/main/2026/03/18/uuid.jpg",
                "https://upload.example.com/presigned",
                "https://cdn.example.com/prod/products/seller-11/main/2026/03/18/uuid.jpg"
        );
        org.mockito.Mockito.when(sellerProductImageService.issuePresignedUploadUrl(seller, request)).thenReturn(payload);

        ApiResponse<ProductImagePresignedUploadResponse> response =
                productSellerController.createPresignedUploadUrl(seller, request);

        verify(sellerProductImageService).issuePresignedUploadUrl(seller, request);
        assertTrue(response.success());
        assertEquals(ProductSuccessCode.PRODUCT_IMAGE_UPLOAD_URL_CREATED.code(), response.code());
        assertEquals(ProductSuccessCode.PRODUCT_IMAGE_UPLOAD_URL_CREATED.message(), response.message());
        assertEquals(payload, response.data());
        assertNotNull(response.timestamp());
    }
}
