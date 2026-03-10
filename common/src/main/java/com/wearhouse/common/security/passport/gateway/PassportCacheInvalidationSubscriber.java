package com.wearhouse.common.security.passport.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wearhouse.common.security.passport.gateway.dto.UserChangedEvent;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class PassportCacheInvalidationSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(PassportCacheInvalidationSubscriber.class);

    private final ObjectMapper objectMapper;
    private final PassportCacheService passportCacheService;

    public PassportCacheInvalidationSubscriber(
            ObjectMapper objectMapper,
            PassportCacheService passportCacheService
    ) {
        this.objectMapper = objectMapper;
        this.passportCacheService = passportCacheService;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String payload = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            UserChangedEvent event = objectMapper.readValue(payload, UserChangedEvent.class);
            if (event.userType() == null || event.userId() == null) {
                log.warn("유효하지 않은 user changed 이벤트를 수신했습니다. payload={}", payload);
                return;
            }
            passportCacheService.evictByUser(event.userType(), event.userId());
            log.info("Passport 캐시 무효화 완료 userType={}, userId={}, reason={}", event.userType(), event.userId(), event.reason());
        } catch (Exception exception) {
            log.warn("Passport 캐시 무효화 이벤트 처리에 실패했습니다. payload={}", payload, exception);
        }
    }
}
