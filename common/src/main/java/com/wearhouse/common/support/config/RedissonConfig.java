package com.wearhouse.common.support.config;

import lombok.RequiredArgsConstructor;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@ConditionalOnClass(RedissonClient.class)
@ConditionalOnProperty(name = "wearhouse.redisson.enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonConfig {

    private final RedisProperties redisProperties;
    private final RedissonProperties redissonProperties;

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean(RedissonClient.class)
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.setLockWatchdogTimeout(redissonProperties.lockWatchdogTimeoutMs());
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress(redisAddress())
                .setDatabase(redisProperties.getDatabase());
        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isBlank()) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        return Redisson.create(config);
    }

    private String redisAddress() {
        return "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();
    }
}
