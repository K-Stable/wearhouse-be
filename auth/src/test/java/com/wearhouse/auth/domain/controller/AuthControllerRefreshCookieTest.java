package com.wearhouse.auth.domain.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.domain.service.command.AuthCommandService;
import com.wearhouse.auth.domain.service.command.AuthCommandService.AuthSession;
import com.wearhouse.auth.support.cookie.AuthCookieService;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
class AuthControllerRefreshCookieTest {

    @Mock
    private AuthCommandService authCommandService;
    @Mock
    private AuthCookieService authCookieService;

    @Test
    void refreshSellerShouldReadRefreshTokenFromCookieAndRotateCookie() {
        AuthController authController = new AuthController(authCommandService, authCookieService, "internal-secret");
        AuthSession session = new AuthSession(
                new AuthTokenResponse(
                        21L,
                        "SELLER",
                        "seller@example.com",
                        LocalDateTime.now().plusMinutes(30),
                        "new-access-token",
                        "new-refresh-token"
                ),
                "new-access-token",
                "new-refresh-token"
        );
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(authCookieService.resolveSellerRefreshToken(request)).thenReturn("refresh-from-cookie");
        when(authCommandService.refresh(AuthUserType.SELLER, "refresh-from-cookie")).thenReturn(session);

        AuthTokenResponse tokenResponse = authController.refreshSeller(request, response);

        verify(authCookieService).resolveSellerRefreshToken(request);
        verify(authCommandService).refresh(AuthUserType.SELLER, "refresh-from-cookie");
        verify(authCookieService).writeSellerRefreshToken(response, "new-refresh-token");
        assertEquals("new-access-token", tokenResponse.accessToken());
        assertNull(tokenResponse.refreshToken());
    }
}
