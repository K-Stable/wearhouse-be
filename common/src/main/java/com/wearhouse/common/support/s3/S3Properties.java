package com.wearhouse.common.support.s3;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
@ConfigurationProperties(prefix = "wearhouse.s3")
public class S3Properties {

    private boolean enabled = false;
    private String accessKey;
    private String secretKey;
    private String region = "ap-northeast-2";
    private String bucket;
    private String publicBaseUrl;
    private long presignedPutExpireSeconds = 300;
}
