package com.wearhouse.auth.support.security;

import com.wearhouse.auth.domain.exception.AuthErrorCode;
import com.wearhouse.auth.domain.model.AuthAccount;
import com.wearhouse.auth.domain.model.AuthUserType;
import com.wearhouse.auth.support.config.AuthInternalSharedSecret;
import com.wearhouse.common.global.error.ErrorException;
import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.common.infra.feign.userauth.UserAuthFeignClient;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthAccountResponse;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthByIdRequest;
import com.wearhouse.common.infra.feign.userauth.dto.UserAuthByLoginIdRequest;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PrincipalDetailsService {

    private final UserAuthFeignClient userAuthFeignClient;
    private final AuthInternalSharedSecret internalSharedSecret;

    public AuthAccount getAccountByLoginId(AuthUserType userType, String loginId) {
        try {
            ApiResponse<UserAuthAccountResponse> response = userAuthFeignClient.findByLoginId(
                    internalSharedSecret.value(),
                    new UserAuthByLoginIdRequest(userType.name(), loginId)
            );
            return toAuthAccount(unwrapData(response, AuthErrorCode.USER_SERVICE_INVALID_RESPONSE));
        } catch (FeignException.NotFound exception) {
            throw new ErrorException(AuthErrorCode.INVALID_CREDENTIALS);
        } catch (FeignException exception) {
            throw new ErrorException(AuthErrorCode.USER_SERVICE_UNAVAILABLE);
        }
    }

    public AuthAccount getAccountById(AuthUserType userType, Long userId) {
        try {
            ApiResponse<UserAuthAccountResponse> response = userAuthFeignClient.findById(
                    internalSharedSecret.value(),
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
}
