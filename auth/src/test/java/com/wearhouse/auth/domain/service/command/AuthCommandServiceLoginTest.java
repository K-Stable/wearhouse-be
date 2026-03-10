package com.wearhouse.auth.domain.service.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthAccount;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.support.event.AuthUserChangedPublisher;
import com.wearhouse.auth.support.jwt.JwtTokenService;
import com.wearhouse.auth.support.security.PrincipalDetailsService;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.jwt.JwtProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthCommandServiceLoginTest {

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
    void loginShouldUseLoginIdForAccountLookup() {
        AuthCommandService authCommandService = new AuthCommandService(
                jwtTokenService,
                passwordEncoder,
                authUserChangedPublisher,
                principalDetailsService,
                jwtProvider
        );
        LoginRequest request = new LoginRequest("buyer01", "plain-password");

        when(principalDetailsService.getAccountByLoginId(any(), any()))
                .thenReturn(new AuthAccount(
                        1L,
                        "buyer@example.com",
                        "encoded-password",
                        null,
                        "ACTIVE",
                        1L,
                        AuthUserType.BUYER
                ));
        when(passwordEncoder.matches("plain-password", "encoded-password")).thenReturn(false);

        ErrorException exception = assertThrows(
                ErrorException.class,
                () -> authCommandService.login(AuthUserType.BUYER, request, new MockHttpServletResponse())
        );

        assertEquals(AuthErrorCode.INVALID_CREDENTIALS, exception.errorCode());
    }
}
