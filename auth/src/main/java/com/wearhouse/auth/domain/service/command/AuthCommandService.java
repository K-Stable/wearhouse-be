package com.wearhouse.auth.domain.service.command;

import com.wearhouse.auth.domain.dto.request.LoginRequest;
import com.wearhouse.auth.domain.dto.request.SignupRequest;
import com.wearhouse.auth.domain.dto.response.AuthTokenResponse;
import com.wearhouse.auth.domain.dto.response.InternalValidateResponse;
import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthAccount;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.infra.feign.UserAuthFeignClient;
import com.wearhouse.auth.infra.feign.dto.UserAuthAccountResponse;
import com.wearhouse.auth.infra.feign.dto.UserAuthByIdRequest;
import com.wearhouse.auth.infra.feign.dto.UserAuthByLoginIdRequest;
import com.wearhouse.auth.infra.feign.dto.UserAuthSignupRequest;
import com.wearhouse.auth.infra.redis.RefreshTokenStore;
import com.wearhouse.auth.support.event.AuthUserChangedPublisher;
import com.wearhouse.auth.support.jwt.JwtTokenProvider;
import com.wearhouse.auth.support.jwt.JwtTokenProvider.DecodedAccessToken;
import com.wearhouse.auth.support.jwt.JwtTokenProvider.IssuedAccessToken;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import feign.FeignException;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthCommandService {

    private final UserAuthFeignClient userAuthFeignClient;
    private final RefreshTokenStore refreshTokenStore;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final AuthUserChangedPublisher authUserChangedPublisher;
    private final String internalSharedSecret;

    public AuthCommandService(
            UserAuthFeignClient userAuthFeignClient,
            RefreshTokenStore refreshTokenStore,
            JwtTokenProvider jwtTokenProvider,
            PasswordEncoder passwordEncoder,
            AuthUserChangedPublisher authUserChangedPublisher,
            @Value("${wearhouse.auth.internal.shared-secret}") String internalSharedSecret
    ) {
        this.userAuthFeignClient = userAuthFeignClient;
        this.refreshTokenStore = refreshTokenStore;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
        this.authUserChangedPublisher = authUserChangedPublisher;
        this.internalSharedSecret = internalSharedSecret;
    }

    public AuthSession signup(AuthUserType userType, SignupRequest request) {
        AuthAccount account;
        try {
            ApiResponse<UserAuthAccountResponse> response = userAuthFeignClient.signup(
                    internalSharedSecret,
                    new UserAuthSignupRequest(
                            userType.name(),
                            request.email(),
                            passwordEncoder.encode(request.password()),
                            request.displayName()
                    )
            );
            account = toAuthAccount(unwrapData(response, AuthErrorCode.USER_SERVICE_INVALID_RESPONSE));
        } catch (FeignException.Conflict exception) {
            throw new ErrorException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        } catch (FeignException exception) {
            throw new ErrorException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
        }

        return createSession(account);
    }

    public AuthSession login(AuthUserType userType, LoginRequest request) {
        AuthAccount account = getAccountByLoginId(userType, request.loginId());

        if (!account.isActive()) {
            throw new ErrorException(AuthErrorCode.USER_NOT_ACTIVE);
        }

        if (!passwordEncoder.matches(request.password(), account.passwordHash())) {
            throw new ErrorException(AuthErrorCode.INVALID_CREDENTIALS);
        }

        return createSession(account);
    }

    public AuthSession refresh(AuthUserType userType, String refreshToken) {
        Long userId = refreshTokenStore.resolve(userType, refreshToken)
                .orElseThrow(() -> new ErrorException(AuthErrorCode.REFRESH_TOKEN_INVALID));

        AuthAccount account = getAccountById(userType, userId);

        if (!account.isActive()) {
            throw new ErrorException(AuthErrorCode.USER_NOT_ACTIVE);
        }

        refreshTokenStore.revoke(userType, refreshToken);
        return createSession(account);
    }

    public void logout(AuthUserType userType, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Long userId = refreshTokenStore.resolve(userType, refreshToken).orElse(null);
        refreshTokenStore.revoke(userType, refreshToken);
        if (userId != null) {
            authUserChangedPublisher.publish(userType, userId, "LOGOUT");
        }
    }

    public InternalValidateResponse validateAccessToken(String accessToken) {
        DecodedAccessToken decoded;
        try {
            decoded = jwtTokenProvider.decodeAccessToken(accessToken);
        } catch (RuntimeException exception) {
            throw new ErrorException(AuthErrorCode.ACCESS_TOKEN_INVALID);
        }

        AuthAccount account = getAccountById(decoded.userType(), decoded.userId());

        if (!account.isActive()) {
            throw new ErrorException(AuthErrorCode.USER_NOT_ACTIVE);
        }

        return new InternalValidateResponse(
                account.id(),
                account.userType().name(),
                decoded.roles(),
                account.userVersion(),
                decoded.issuedAt(),
                decoded.expiresAt()
        );
    }

    private AuthSession createSession(AuthAccount account) {
        List<String> roles = List.of(account.userType().role());
        IssuedAccessToken issuedAccessToken = jwtTokenProvider.issueAccessToken(
                account.id(),
                account.userType(),
                roles,
                account.userVersion()
        );
        String refreshToken = refreshTokenStore.issue(account.userType(), account.id());

        AuthTokenResponse response = new AuthTokenResponse(
                account.id(),
                account.userType().name(),
                account.email(),
                issuedAccessToken.expiresAt(),
                issuedAccessToken.accessToken(),
                refreshToken
        );

        return new AuthSession(
                response,
                issuedAccessToken.accessToken(),
                refreshToken
        );
    }

    private AuthAccount getAccountByLoginId(AuthUserType userType, String loginId) {
        try {
            ApiResponse<UserAuthAccountResponse> response = userAuthFeignClient.findByLoginId(
                    internalSharedSecret,
                    new UserAuthByLoginIdRequest(userType.name(), loginId)
            );
            return toAuthAccount(unwrapData(response, AuthErrorCode.USER_SERVICE_INVALID_RESPONSE));
        } catch (FeignException.NotFound exception) {
            throw new ErrorException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (FeignException exception) {
            throw new ErrorException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
        }
    }

    private AuthAccount getAccountById(AuthUserType userType, Long userId) {
        try {
            ApiResponse<UserAuthAccountResponse> response = userAuthFeignClient.findById(
                    internalSharedSecret,
                    new UserAuthByIdRequest(userType.name(), userId)
            );
            return toAuthAccount(unwrapData(response, AuthErrorCode.USER_SERVICE_INVALID_RESPONSE));
        } catch (FeignException.NotFound exception) {
            throw new ErrorException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (FeignException exception) {
            throw new ErrorException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
        }
    }

    private UserAuthAccountResponse unwrapData(ApiResponse<UserAuthAccountResponse> response, AuthErrorCode errorCode) {
        if (response == null || !response.success() || response.data() == null) {
            throw new ErrorException(errorCode);
        }
        return response.data();
    }

    private AuthAccount toAuthAccount(UserAuthAccountResponse response) {
        return new AuthAccount(
                response.userId(),
                response.email(),
                response.passwordHash(),
                null,
                response.status(),
                response.userVersion(),
                AuthUserType.valueOf(response.userType())
        );
    }

    public record AuthSession(
            AuthTokenResponse response,
            String accessToken,
            String refreshToken
    ) {
    }
}
