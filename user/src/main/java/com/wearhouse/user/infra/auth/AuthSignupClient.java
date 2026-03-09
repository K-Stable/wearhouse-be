package com.wearhouse.user.infra.auth;

import com.wearhouse.common.global.response.ApiResponse;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupRequest;
import com.wearhouse.user.infra.auth.dto.AuthInternalSignupResponse;
import com.wearhouse.user.infra.feign.AuthSignupFeignClient;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthSignupClient {

    private final AuthSignupFeignClient authSignupFeignClient;
    private final String internalSharedSecret;

    public AuthSignupClient(
            AuthSignupFeignClient authSignupFeignClient,
            @Value("${wearhouse.user.auth.internal-shared-secret}") String internalSharedSecret
    ) {
        this.authSignupFeignClient = authSignupFeignClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    public AuthInternalSignupResponse signupBuyer(String email, String password, String displayName) {
        return signup(true, email, password, displayName);
    }

    public AuthInternalSignupResponse signupSeller(String email, String password, String displayName) {
        return signup(false, email, password, displayName);
    }

    private AuthInternalSignupResponse signup(boolean buyer, String email, String password, String displayName) {
        try {
            ApiResponse<AuthInternalSignupResponse> response = buyer
                    ? authSignupFeignClient.signupBuyer(internalSharedSecret, new AuthInternalSignupRequest(email, password, displayName))
                    : authSignupFeignClient.signupSeller(internalSharedSecret, new AuthInternalSignupRequest(email, password, displayName));
            if (response == null || !response.success() || response.data() == null) {
                throw new IllegalStateException("auth-service signup 응답이 유효하지 않습니다.");
            }
            return response.data();
        } catch (FeignException exception) {
            throw new IllegalStateException("auth-service signup 호출 실패: " + exception.status(), exception);
        }
    }
}
