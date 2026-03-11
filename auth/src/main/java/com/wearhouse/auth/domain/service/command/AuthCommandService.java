package com.wearhouse.auth.domain.service.command;

import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.dto.response.InternalValidateResponse;
import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthAccount;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.support.jwt.JwtTokenService;
import com.wearhouse.auth.support.event.AuthUserChangedPublisher;
import com.wearhouse.auth.support.security.PrincipalDetailsService;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.jwt.JwtProvider;
import com.wearhouse.common.security.jwt.JwtProvider.DecodedAccessToken;
import com.wearhouse.common.security.jwt.JwtProvider.IssuedAccessToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthCommandService {

    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserChangedPublisher authUserChangedPublisher;
    private final PrincipalDetailsService principalDetailsService;
    private final JwtProvider jwtProvider;

    public AuthTokenResponse login(AuthUserType userType, LoginRequest request, HttpServletResponse response) {
        AuthAccount account = principalDetailsService.getAccountByLoginId(userType, request.loginId());
        validateActiveAccount(account);
        validatePassword(account, request.password());
        return issueTokens(account, response);
    }

    public AuthTokenResponse refresh(AuthUserType userType, HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = requireRefreshToken(jwtProvider.extractRefreshToken(request, userType.name()));
        Long userId = consumeRefreshToken(userType, refreshToken);
        AuthAccount account = principalDetailsService.getAccountById(userType, userId);
        validateActiveAccount(account);
        return issueTokens(account, response);
    }

    public void logout(AuthUserType userType, HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = jwtProvider.extractRefreshToken(request, userType.name());
        if (refreshToken == null || refreshToken.isBlank()) {
            jwtProvider.clearRefreshToken(response, userType.name());
            return;
        }
        Long userId = jwtTokenService.resolveRefreshToken(userType.name(), refreshToken).orElse(null);
        jwtTokenService.logout(userType.name(), refreshToken);
        jwtProvider.clearRefreshToken(response, userType.name());
        publishLogoutIfPresent(userType, userId);
    }

    public InternalValidateResponse validateAccessToken(String accessToken) {
        DecodedAccessToken decoded = decodeAccessToken(accessToken);
        AuthUserType decodedUserType = AuthUserType.valueOf(decoded.userType());
        AuthAccount account = principalDetailsService.getAccountById(decodedUserType, decoded.userId());
        validateActiveAccount(account);

        return new InternalValidateResponse(
                account.id(),
                account.userType().name(),
                decoded.roles(),
                account.userVersion(),
                decoded.issuedAt(),
                decoded.expiresAt()
        );
    }

    private AuthTokenResponse issueTokens(AuthAccount account, HttpServletResponse response) {
        IssuedAccessToken issuedAccessToken = jwtTokenService.issueAccessToken(account);
        String refreshToken = jwtTokenService.issueRefreshToken(account);
        jwtProvider.applyTokens(response, account.userType().name(), issuedAccessToken.accessToken(), refreshToken);
        return toClientResponse(new AuthTokenResponse(
                account.id(),
                account.userType().name(),
                account.email(),
                issuedAccessToken.expiresAt(),
                issuedAccessToken.accessToken(),
                refreshToken
        ));
    }

    private void validatePassword(AuthAccount account, String rawPassword) {
        if (!passwordEncoder.matches(rawPassword, account.passwordHash())) {
            throw new ErrorException(AuthErrorCode.INVALID_CREDENTIALS);
        }
    }

    private void validateActiveAccount(AuthAccount account) {
        if (!account.isActive()) {
            throw new ErrorException(AuthErrorCode.USER_NOT_ACTIVE);
        }
    }

    private Long consumeRefreshToken(AuthUserType userType, String refreshToken) {
        try {
            return jwtTokenService.consumeRefreshToken(userType.name(), refreshToken);
        } catch (ErrorException exception) {
            throw new ErrorException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
    }

    private DecodedAccessToken decodeAccessToken(String accessToken) {
        try {
            return jwtTokenService.decodeAccessToken(accessToken);
        } catch (RuntimeException exception) {
            throw new ErrorException(AuthErrorCode.ACCESS_TOKEN_INVALID);
        }
    }

    private void publishLogoutIfPresent(AuthUserType userType, Long userId) {
        if (userId == null) {
            return;
        }
        authUserChangedPublisher.publish(userType, userId, "LOGOUT");
    }

    private String requireRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ErrorException(AuthErrorCode.REFRESH_TOKEN_INVALID);
        }
        return refreshToken;
    }

    private AuthTokenResponse toClientResponse(AuthTokenResponse response) {
        return new AuthTokenResponse(
                response.userId(),
                response.userType(),
                response.email(),
                response.accessTokenExpiresAt(),
                response.accessToken(),
                null
        );
    }

}
