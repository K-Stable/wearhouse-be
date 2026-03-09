package com.wearhouse.apigateway.security;

import com.wearhouse.apigateway.security.dto.AuthValidateRequest;
import com.wearhouse.apigateway.security.dto.AuthValidateResponse;
import com.wearhouse.apigateway.security.feign.AuthValidateFeignClient;
import com.wearhouse.common.global.response.ApiResponse;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AuthValidateClient {

    private final AuthValidateFeignClient authValidateFeignClient;
    private final String internalSharedSecret;

    public AuthValidateClient(
            AuthValidateFeignClient authValidateFeignClient,
            @Value("${wearhouse.gateway.auth.internal-shared-secret}") String internalSharedSecret
    ) {
        this.authValidateFeignClient = authValidateFeignClient;
        this.internalSharedSecret = internalSharedSecret;
    }

    public AuthValidateResponse validate(String accessToken) {
        try {
            ApiResponse<AuthValidateResponse> response = authValidateFeignClient.validate(
                    internalSharedSecret,
                    new AuthValidateRequest(accessToken)
            );
            if (response == null || !response.success() || response.data() == null) {
                throw new IllegalStateException("auth-service validate 응답이 유효하지 않습니다.");
            }
            return response.data();
        } catch (FeignException exception) {
            throw new IllegalStateException("auth-service validate 호출 실패: " + exception.status(), exception);
        }
    }
}
