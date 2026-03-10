package com.wearhouse.auth.domain.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.infra.feign.UserAuthFeignClient;
import com.wearhouse.auth.infra.feign.dto.UserAuthAccountResponse;
import com.wearhouse.auth.infra.redis.RefreshTokenStore;
import com.wearhouse.auth.support.event.AuthUserChangedPublisher;
import com.wearhouse.auth.support.jwt.JwtTokenProvider;
import com.wearhouse.auth.support.jwt.JwtTokenProvider.IssuedAccessToken;
import com.wearhouse.common.global.response.ApiResponse;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTokenResponseTest {

    @Mock
    private UserAuthFeignClient userAuthFeignClient;
    @Mock
    private RefreshTokenStore refreshTokenStore;
    @Mock
    private JwtTokenProvider jwtTokenProvider;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthUserChangedPublisher authUserChangedPublisher;

    @Test
    void loginShouldReturnAccessAndRefreshTokensInResponse() {
        AuthCommandService authCommandService = new AuthCommandService(
                userAuthFeignClient,
                refreshTokenStore,
                jwtTokenProvider,
                passwordEncoder,
                authUserChangedPublisher,
                "internal-secret"
        );

        when(userAuthFeignClient.findByLoginId(any(), any()))
                .thenReturn(ApiResponse.success(new UserAuthAccountResponse(
                        11L,
                        "SELLER",
                        "seller@example.com",
                        "encoded-password",
                        "ACTIVE",
                        1L
                )));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(30);
        when(jwtTokenProvider.issueAccessToken(11L, AuthUserType.SELLER, java.util.List.of("ROLE_SELLER"), 1L))
                .thenReturn(new IssuedAccessToken("access-token-value", issuedAt, expiresAt));
        when(refreshTokenStore.issue(AuthUserType.SELLER, 11L)).thenReturn("refresh-token-value");

        AuthCommandService.AuthSession session = authCommandService.login(
                AuthUserType.SELLER,
                new LoginRequest("seller01", "plain-password")
        );

        assertEquals("access-token-value", session.response().accessToken());
        assertEquals("refresh-token-value", session.response().refreshToken());
    }
}
