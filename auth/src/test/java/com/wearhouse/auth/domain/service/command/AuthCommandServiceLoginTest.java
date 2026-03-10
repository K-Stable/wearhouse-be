package com.wearhouse.auth.domain.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.infra.feign.UserAuthFeignClient;
import com.wearhouse.auth.infra.feign.dto.UserAuthAccountResponse;
import com.wearhouse.auth.infra.feign.dto.UserAuthByLoginIdRequest;
import com.wearhouse.auth.infra.redis.RefreshTokenStore;
import com.wearhouse.auth.support.event.AuthUserChangedPublisher;
import com.wearhouse.auth.support.jwt.JwtTokenProvider;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceLoginTest {

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
    void loginShouldUseLoginIdForAccountLookup() {
        AuthCommandService authCommandService = new AuthCommandService(
                userAuthFeignClient,
                refreshTokenStore,
                jwtTokenProvider,
                passwordEncoder,
                authUserChangedPublisher,
                "internal-secret"
        );
        LoginRequest request = new LoginRequest("buyer01", "plain-password");

        when(userAuthFeignClient.findByLoginId(any(), any(UserAuthByLoginIdRequest.class)))
                .thenReturn(ApiResponse.success(new UserAuthAccountResponse(
                        1L,
                        "BUYER",
                        "buyer@example.com",
                        "encoded-password",
                        "ACTIVE",
                        1L
                )));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(false);

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> authCommandService.login(AuthUserType.BUYER, request)
        );

        verify(userAuthFeignClient).findByLoginId("internal-secret", new UserAuthByLoginIdRequest("BUYER", "buyer01"));
        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, exception.errorCode());
    }
}
