package com.wearhouse.auth.domain.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.model.AuthAccount;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.support.event.AuthUserChangedPublisher;
import com.wearhouse.auth.support.jwt.JwtTokenService;
import com.wearhouse.auth.support.security.PrincipalDetailsService;
import com.wearhouse.common.security.jwt.JwtProvider;
import com.wearhouse.common.security.jwt.JwtProvider.IssuedAccessToken;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceTokenResponseTest {

    @Mock
    private JwtTokenService jwtTokenService;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuthUserChangedPublisher authUserChangedPublisher;
    @Mock
    private PrincipalDetailsService principalDetailsService;
    @Mock
    private JwtProvider jwtProvider;

    @Test
    void loginShouldReturnAccessAndRefreshTokensInResponse() {
        AuthCommandService authCommandService = new AuthCommandService(
                jwtTokenService,
                passwordEncoder,
                authUserChangedPublisher,
                principalDetailsService,
                jwtProvider
        );

        when(principalDetailsService.getAccountByLoginId(any(), any()))
                .thenReturn(new AuthAccount(
                        11L,
                        "seller@example.com",
                        "encoded-password",
                        null,
                        "ACTIVE",
                        1L,
                        AuthUserType.SELLER
                ));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(true);

        LocalDateTime issuedAt = LocalDateTime.now();
        LocalDateTime expiresAt = issuedAt.plusMinutes(30);
        when(jwtTokenService.issueAccessToken(any(AuthAccount.class)))
                .thenReturn(new IssuedAccessToken("access-token-value", issuedAt, expiresAt));
        when(jwtTokenService.issueRefreshToken(any(AuthAccount.class))).thenReturn("refresh-token-value");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AuthTokenResponse tokenResponse = authCommandService.login(
                AuthUserType.SELLER,
                new LoginRequest("seller01", "plain-password"),
                response
        );

        verify(jwtProvider).applyTokens(response, "SELLER", "access-token-value", "refresh-token-value");
        assertEquals("access-token-value", tokenResponse.accessToken());
        assertNull(tokenResponse.refreshToken());
    }
}
