package com.wearhouse.auth.domain.controller;

import com.wearhouse.auth.domain.dto.request.InternalValidateRequest;
import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.dto.response.InternalValidateResponse;
import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.domain.service.command.AuthCommandService;
import com.wearhouse.auth.support.config.AuthInternalSharedSecret;
import com.wearhouse.common.global.error.ErrorException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final AuthCommandService authCommandService;
    private final AuthInternalSharedSecret internalSharedSecret;

    @PostMapping("/auth/buyers/login")
    public AuthTokenResponse loginBuyer(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return authCommandService.login(AuthUserType.BUYER, request, response);
    }

    @PostMapping("/auth/buyers/refresh")
    public AuthTokenResponse refreshBuyer(HttpServletRequest request, HttpServletResponse response) {
        return authCommandService.refresh(AuthUserType.BUYER, request, response);
    }

    @PostMapping("/auth/buyers/logout")
    public void logoutBuyer(HttpServletRequest request, HttpServletResponse response) {
        authCommandService.logout(AuthUserType.BUYER, request, response);
    }

    @PostMapping("/auth/sellers/login")
    public AuthTokenResponse loginSeller(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        return authCommandService.login(AuthUserType.SELLER, request, response);
    }

    @PostMapping("/auth/sellers/refresh")
    public AuthTokenResponse refreshSeller(HttpServletRequest request, HttpServletResponse response) {
        return authCommandService.refresh(AuthUserType.SELLER, request, response);
    }

    @PostMapping("/auth/sellers/logout")
    public void logoutSeller(HttpServletRequest request, HttpServletResponse response) {
        authCommandService.logout(AuthUserType.SELLER, request, response);
    }

    @PostMapping("/internal/auth/validate")
    public InternalValidateResponse validateAccessToken(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InternalValidateRequest request
    ) {
        if (!internalSharedSecret.value().equals(headerSecret)) {
            throw new ErrorException(AuthErrorCode.INTERNAL_SECRET_INVALID);
        }
        return authCommandService.validateAccessToken(request.accessToken());
    }
}
