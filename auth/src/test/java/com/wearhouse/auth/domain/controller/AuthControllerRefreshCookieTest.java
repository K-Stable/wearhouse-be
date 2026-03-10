package com.wearhouse.auth.domain.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.domain.service.command.AuthCommandService;
import com.wearhouse.auth.support.config.AuthInternalSharedSecret;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshCookieTest {

    @Mock
    private AuthCommandService authCommandService;
    @Mock
    private AuthInternalSharedSecret internalSharedSecret;

    @InjectMocks
    private AuthController authController;

    @Test
    void refreshSellerShouldReadRefreshTokenFromCookieAndRotateCookie() {
        AuthTokenResponse expected = new AuthTokenResponse(
                21L,
                "SELLER",
                "seller@example.com",
                LocalDateTime.now().plusMinutes(30),
                "new-access-token",
                null
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authCommandService.refresh(AuthUserType.SELLER, request, response)).thenReturn(expected);

        AuthTokenResponse tokenResponse = authController.refreshSeller(request, response);

        verify(authCommandService).refresh(AuthUserType.SELLER, request, response);
        assertEquals("new-access-token", tokenResponse.accessToken());
        assertNull(tokenResponse.refreshToken());
    }
}
