package com.wearhouse.product.domain.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.support.s3.S3PresignedUploadResult;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.dto.request.ProductImagePresignedUploadRequest;
import com.wearhouse.product.domain.dto.response.ProductImagePresignedUploadResponse;
import com.wearhouse.product.domain.model.ProductImageType;
import com.wearhouse.product.domain.service.seller.SellerProductImageService;
import com.wearhouse.product.support.config.ProductImageStorageProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SellerProductImageServiceTest {

    @Mock
    private S3StorageService s3StorageService;

    @Test
    void issuePresignedUploadUrlShouldBuildMainImageKeyAndReturnUrls() {
        ProductImageStorageProperties properties = new ProductImageStorageProperties();
        properties.setKeyPrefix("prod");
        SellerProductImageService service = new SellerProductImageService(s3StorageService, properties);

        LoginUser seller = new LoginUser(11L, "SELLER", List.of("ROLE_SELLER"), 1L);
        ProductImagePresignedUploadRequest request = new ProductImagePresignedUploadRequest(
                "my-main-image.jpg",
                "image/jpeg",
                ProductImageType.MAIN
        );
        when(s3StorageService.uploadImage(any(), eq("image/jpeg")))
                .thenReturn(new S3PresignedUploadResult("https://upload.example.com/presigned"));
        when(s3StorageService.getImageUrl(any()))
                .thenReturn("https://cdn.example.com/prod/products/seller-11/main/yyyy/mm/dd/uuid.jpg");

        ProductImagePresignedUploadResponse response = service.issuePresignedUploadUrl(seller, request);

        assertEquals("https://upload.example.com/presigned", response.uploadUrl());
        assertTrue(response.imageKey().startsWith("prod/products/seller-11/main/"));
        assertTrue(response.imageKey().endsWith(".jpg"));
        assertEquals("https://cdn.example.com/prod/products/seller-11/main/yyyy/mm/dd/uuid.jpg", response.imageUrl());
        verify(s3StorageService).uploadImage(any(), eq("image/jpeg"));
        verify(s3StorageService).getImageUrl(response.imageKey());
    }
}
