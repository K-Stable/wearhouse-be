package com.wearhouse.auth.support.jwt;

import com.wearhouse.auth.domain.model.AuthAccount;
import com.wearhouse.auth.infra.redis.RefreshTokenStore;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.security.jwt.JwtProvider;
import com.wearhouse.common.security.jwt.JwtProvider.DecodedRefreshToken;
import com.wearhouse.common.security.jwt.exception.JwtErrorCode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

    private final JwtProvider jwtProvider;
    private final RefreshTokenStore refreshTokenStore;

    public JwtProvider.IssuedAccessToken issueAccessToken(AuthAccount account) {
        return jwtProvider.issueAccessToken(
                account.id(),
                account.userType().name(),
                account.email(),
                account.userVersion()
        );
    }

    public String issueRefreshToken(AuthAccount account) {
        JwtProvider.IssuedRefreshToken issuedRefreshToken = jwtProvider.issueRefreshToken(
                account.id(),
                account.userType().name(),
                account.email(),
                account.userVersion()
        );
        refreshTokenStore.save(account.userType().name(), issuedRefreshToken.refreshToken(), account.id());
        return issuedRefreshToken.refreshToken();
    }

    public Optional<Long> resolveRefreshToken(String userType, String refreshToken) {
        return refreshTokenStore.findUserId(userType, refreshToken);
    }

    public Long consumeRefreshToken(String userType, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw invalidRefreshToken();
        }
        DecodedRefreshToken decoded = decodeRefreshToken(refreshToken);
        if (!decoded.userType().equalsIgnoreCase(userType)) {
            throw invalidRefreshToken();
        }

        Long storedUserId = refreshTokenStore.consumeUserId(userType, refreshToken)
                .orElseThrow(this::invalidRefreshToken);
        if (!storedUserId.equals(decoded.userId())) {
            throw invalidRefreshToken();
        }
        return storedUserId;
    }

    public void logout(String userType, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        refreshTokenStore.delete(userType, refreshToken);
    }

    public JwtProvider.DecodedAccessToken decodeAccessToken(String accessToken) {
        return jwtProvider.decodeAccessToken(accessToken);
    }

    private DecodedRefreshToken decodeRefreshToken(String refreshToken) {
        try {
            return jwtProvider.decodeRefreshToken(refreshToken);
        } catch (RuntimeException exception) {
            throw invalidRefreshToken();
        }
    }

    private ErrorException invalidRefreshToken() {
        return new ErrorException(JwtErrorCode.REFRESH_TOKEN_INVALID);
    }
}
