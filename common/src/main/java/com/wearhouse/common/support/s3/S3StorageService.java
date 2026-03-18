package com.wearhouse.common.support.s3;

import java.time.Duration;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private final S3Properties properties;
    private final ObjectProvider<S3Client> s3ClientProvider;
    private final ObjectProvider<S3Presigner> s3PresignerProvider;

    public S3PresignedUploadResult uploadImage(@NonNull String key, String contentType) {
        S3Presigner presigner = requirePresigner();
        PutObjectRequest.Builder putObjectRequestBuilder = PutObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key);
        if (StringUtils.hasText(contentType)) {
            putObjectRequestBuilder.contentType(contentType);
        }

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofSeconds(properties.getPresignedPutExpireSeconds()))
                .putObjectRequest(putObjectRequestBuilder.build())
                .build();
        PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
        return new S3PresignedUploadResult(presignedRequest.url().toString());
    }

    public String getImageUrl(String key) {
        if (!StringUtils.hasText(key)) {
            return key;
        }
        if (isAbsoluteUrl(key)) {
            return key;
        }
        if (StringUtils.hasText(properties.getPublicBaseUrl())) {
            return appendPath(properties.getPublicBaseUrl(), key);
        }
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null) {
            return key;
        }
        return s3Client.utilities()
                .getUrl(GetUrlRequest.builder().bucket(properties.getBucket()).key(key).build())
                .toExternalForm();
    }

    public void deleteImage(@NonNull String key) {
        S3Client s3Client = requireS3Client();
        s3Client.deleteObject(DeleteObjectRequest.builder()
                .bucket(properties.getBucket())
                .key(key)
                .build());
    }

    private String appendPath(String baseUrl, String key) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String normalizedKey = key.startsWith("/") ? key.substring(1) : key;
        return normalizedBase + "/" + normalizedKey;
    }

    private boolean isAbsoluteUrl(String value) {
        String normalized = value.toLowerCase();
        return normalized.startsWith("http://") || normalized.startsWith("https://");
    }

    private S3Presigner requirePresigner() {
        S3Presigner presigner = s3PresignerProvider.getIfAvailable();
        if (presigner == null || !properties.isEnabled()) {
            throw new IllegalStateException("S3 is not enabled. Set wearhouse.s3.enabled=true to use uploadImage.");
        }
        return presigner;
    }

    private S3Client requireS3Client() {
        S3Client s3Client = s3ClientProvider.getIfAvailable();
        if (s3Client == null || !properties.isEnabled()) {
            throw new IllegalStateException("S3 is not enabled. Set wearhouse.s3.enabled=true to use deleteImage.");
        }
        return s3Client;
    }
}
