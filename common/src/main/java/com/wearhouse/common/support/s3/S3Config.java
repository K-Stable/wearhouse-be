package com.wearhouse.common.support.s3;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@ConditionalOnProperty(name = "wearhouse.s3.enabled", havingValue = "true")
public class S3Config {

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider(S3Properties properties) {
        Assert.isTrue(StringUtils.hasText(properties.getAccessKey()), "wearhouse.s3.access-key is required");
        Assert.isTrue(StringUtils.hasText(properties.getSecretKey()), "wearhouse.s3.secret-key is required");
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(properties.getAccessKey(), properties.getSecretKey())
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(S3Properties properties, AwsCredentialsProvider awsCredentialsProvider) {
        Assert.isTrue(StringUtils.hasText(properties.getRegion()), "wearhouse.s3.region is required");
        return S3Client.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(S3Properties properties, AwsCredentialsProvider awsCredentialsProvider) {
        Assert.isTrue(StringUtils.hasText(properties.getRegion()), "wearhouse.s3.region is required");
        return S3Presigner.builder()
                .region(Region.of(properties.getRegion()))
                .credentialsProvider(awsCredentialsProvider)
                .build();
    }
}
