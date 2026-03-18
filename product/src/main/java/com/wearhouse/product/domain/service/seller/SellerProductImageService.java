package com.wearhouse.product.domain.service.seller;

import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.current.LoginUser;
import com.wearhouse.common.support.s3.S3PresignedUploadResult;
import com.wearhouse.common.support.s3.S3StorageService;
import com.wearhouse.product.domain.dto.request.ProductImagePresignedUploadRequest;
import com.wearhouse.product.domain.dto.response.ProductImagePresignedUploadResponse;
import com.wearhouse.product.domain.exception.ProductErrorCode;
import com.wearhouse.product.support.config.ProductImageStorageProperties;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SellerProductImageService {

    private static final String SELLER_USER_TYPE = "SELLER";
    private static final ZoneId SEOUL_ZONE_ID = ZoneId.of("Asia/Seoul");

    private final S3StorageService s3StorageService;
    private final ProductImageStorageProperties productImageStorageProperties;

    public ProductImagePresignedUploadResponse issuePresignedUploadUrl(
            LoginUser currentUser,
            ProductImagePresignedUploadRequest request
    ) {
        Long sellerId = requireSellerId(currentUser);
        String imageKey = buildImageKey(sellerId, request.fileName(), request.imageType().name());
        S3PresignedUploadResult presignedUploadResult =
                s3StorageService.uploadImage(imageKey, request.contentType().trim());
        String imageUrl = s3StorageService.getImageUrl(imageKey);
        return new ProductImagePresignedUploadResponse(
                imageKey,
                presignedUploadResult.uploadUrl(),
                imageUrl
        );
    }

    private Long requireSellerId(LoginUser currentUser) {
        if (currentUser == null || currentUser.userId() == null || !SELLER_USER_TYPE.equalsIgnoreCase(currentUser.userType())) {
            throw new ErrorException(ProductErrorCode.FORBIDDEN_PRODUCT_ACCESS);
        }
        return currentUser.userId();
    }

    private String buildImageKey(Long sellerId, String fileName, String imageType) {
        LocalDate today = LocalDate.now(SEOUL_ZONE_ID);
        String extension = extractExtension(fileName);
        String normalizedPrefix = productImageStorageProperties.getKeyPrefix();
        String normalizedType = imageType.toLowerCase(Locale.ROOT);
        return "%s/products/seller-%d/%s/%d/%02d/%02d/%s%s".formatted(
                normalizedPrefix,
                sellerId,
                normalizedType,
                today.getYear(),
                today.getMonthValue(),
                today.getDayOfMonth(),
                UUID.randomUUID(),
                extension
        );
    }

    private String extractExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        String trimmed = fileName.trim();
        int lastDotIndex = trimmed.lastIndexOf('.');
        if (lastDotIndex < 0 || lastDotIndex == trimmed.length() - 1) {
            return "";
        }
        return trimmed.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }
}
