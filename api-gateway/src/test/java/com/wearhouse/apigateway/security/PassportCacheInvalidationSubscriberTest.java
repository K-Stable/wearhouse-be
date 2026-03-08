package com.wearhouse.apigateway.security;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

class PassportCacheInvalidationSubscriberTest {

    private final PassportCacheService passportCacheService = org.mockito.Mockito.mock(PassportCacheService.class);
    private final PassportCacheInvalidationSubscriber subscriber =
            new PassportCacheInvalidationSubscriber(new ObjectMapper(), passportCacheService);

    @Test
    void validEventEvictsUserPassportCache() {
        String payload = "{\"userType\":\"BUYER\",\"userId\":1,\"reason\":\"LOGOUT\"}";
        DefaultMessage message = new DefaultMessage(
                "wearhouse.auth.user-changed.v1".getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(passportCacheService).evictByUser("BUYER", 1L);
    }

    @Test
    void invalidEventDoesNotEvict() {
        String payload = "{\"foo\":\"bar\"}";
        DefaultMessage message = new DefaultMessage(
                "wearhouse.auth.user-changed.v1".getBytes(StandardCharsets.UTF_8),
                payload.getBytes(StandardCharsets.UTF_8)
        );

        subscriber.onMessage(message, null);

        verify(passportCacheService, never()).evictByUser(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
    }
}
