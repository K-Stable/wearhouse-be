package com.wearhouse.auth.support.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.auth.domain.model.AuthUserType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class AuthUserChangedPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final String userChangedChannel;

    public AuthUserChangedPublisher(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${wearhouse.auth.pubsub.user-changed-channel:wearhouse.auth.user-changed.v1}") String userChangedChannel
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.userChangedChannel = userChangedChannel;
    }

    public void publish(AuthUserType userType, Long userId, String reason) {
        try {
            String payload = objectMapper.writeValueAsString(new UserChangedEvent(userType.name(), userId, reason));
            redisTemplate.convertAndSend(userChangedChannel, payload);
        } catch (Exception exception) {
            throw new IllegalStateException("user changed 이벤트 발행에 실패했습니다.", exception);
        }
    }

    private record UserChangedEvent(
            String userType,
            Long userId,
            String reason
    ) {
    }
}
