package com.wearhouse.auth.domain.controller;

import com.wearhouse.auth.domain.dto.request.InternalValidateRequest;
import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.dto.request.SignupRequest;
import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.dto.response.InternalSignupResponse;
import com.wearhouse.auth.domain.dto.response.InternalValidateResponse;
import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.domain.service.command.AuthCommandService;
import com.wearhouse.auth.domain.service.command.AuthCommandService.AuthSession;
import com.wearhouse.auth.support.cookie.AuthCookieService;
import com.wearhouse.common.global.error.ErrorException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private static final String INTERNAL_SECRET_HEADER = "X-Internal-Secret";

    private final AuthCommandService authCommandService;
    private final AuthCookieService authCookieService;
    private final String internalSharedSecret;

    public AuthController(
            AuthCommandService authCommandService,
            AuthCookieService authCookieService,
            @Value("${wearhouse.auth.internal.shared-secret}") String internalSharedSecret
    ) {
        this.authCommandService = authCommandService;
        this.authCookieService = authCookieService;
        this.internalSharedSecret = internalSharedSecret;
    }

    @PostMapping("/auth/buyers/login")
    public AuthTokenResponse loginBuyer(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthSession session = authCommandService.login(AuthUserType.BUYER, request);
        authCookieService.writeBuyerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }

    @PostMapping("/auth/buyers/refresh")
    public AuthTokenResponse refreshBuyer(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService.resolveBuyerRefreshToken(request);
        AuthSession session = authCommandService.refresh(AuthUserType.BUYER, refreshToken);
        authCookieService.writeBuyerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }

    @PostMapping("/auth/buyers/logout")
    public void logoutBuyer(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService.resolveBuyerRefreshToken(request);
        authCommandService.logout(AuthUserType.BUYER, refreshToken);
        authCookieService.clearBuyerTokens(response);
    }

    @PostMapping("/auth/sellers/login")
    public AuthTokenResponse loginSeller(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        AuthSession session = authCommandService.login(AuthUserType.SELLER, request);
        authCookieService.writeSellerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }

    @PostMapping("/auth/sellers/refresh")
    public AuthTokenResponse refreshSeller(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService.resolveSellerRefreshToken(request);
        AuthSession session = authCommandService.refresh(AuthUserType.SELLER, refreshToken);
        authCookieService.writeSellerTokens(response, session.accessToken(), session.refreshToken());
        return session.response();
    }

    @PostMapping("/auth/sellers/logout")
    public void logoutSeller(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = authCookieService.resolveSellerRefreshToken(request);
        authCommandService.logout(AuthUserType.SELLER, refreshToken);
        authCookieService.clearSellerTokens(response);
    }

    @PostMapping("/internal/auth/buyers/signup")
    public InternalSignupResponse signupBuyerInternal(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody SignupRequest request
    ) {
        requireInternalSecret(headerSecret);
        return toInternalSignupResponse(authCommandService.signup(AuthUserType.BUYER, request));
    }

    @PostMapping("/internal/auth/sellers/signup")
    public InternalSignupResponse signupSellerInternal(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody SignupRequest request
    ) {
        requireInternalSecret(headerSecret);
        return toInternalSignupResponse(authCommandService.signup(AuthUserType.SELLER, request));
    }

    @PostMapping("/internal/auth/validate")
    public InternalValidateResponse validateAccessToken(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String headerSecret,
            @Valid @RequestBody InternalValidateRequest request
    ) {
        requireInternalSecret(headerSecret);
        return authCommandService.validateAccessToken(request.accessToken());
    }

    private void requireInternalSecret(String headerSecret) {
        if (!internalSharedSecret.equals(headerSecret)) {
            throw new ErrorException(AuthErrorCode.INTERNAL_SECRET_INVALID);
        }
    }

    private InternalSignupResponse toInternalSignupResponse(AuthSession session) {
        return new InternalSignupResponse(
                session.response().userId(),
                session.response().userType(),
                session.response().email(),
                session.response().accessTokenExpiresAt(),
                session.accessToken(),
                session.refreshToken()
        );
    }
}
