package com.wearhouse.apigateway.security;

import com.wearhouse.apigateway.security.dto.AuthValidateResponse;
import com.wearhouse.apigateway.security.dto.PassportContext;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class GatewayPassportService {

    private final PassportCacheService passportCacheService;
    private final AuthValidateClient authValidateClient;

    public GatewayPassportService(
            PassportCacheService passportCacheService,
            AuthValidateClient authValidateClient
    ) {
        this.passportCacheService = passportCacheService;
        this.authValidateClient = authValidateClient;
    }

    public PassportContext resolve(String accessToken) {
        Optional<PassportContext> cached = passportCacheService.get(accessToken);
        if (cached.isPresent()) {
            return cached.get();
        }

        AuthValidateResponse response = authValidateClient.validate(accessToken);
        PassportContext passportContext = new PassportContext(
                response.userId(),
                response.userType(),
                response.roles(),
                response.userVersion(),
                response.tokenIssuedAt(),
                response.tokenExpiresAt()
        );
        passportCacheService.put(accessToken, passportContext);
        return passportContext;
    }
}
