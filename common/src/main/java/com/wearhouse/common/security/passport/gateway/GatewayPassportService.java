package com.wearhouse.common.security.passport.gateway;

import com.wearhouse.common.security.jwt.JwtProvider;
import com.wearhouse.common.security.jwt.JwtProvider.DecodedAccessToken;
import com.wearhouse.common.security.passport.gateway.dto.PassportContext;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "spring.application.name", havingValue = "api-gateway")
public class GatewayPassportService {

    private final PassportCacheService passportCacheService;
    private final JwtProvider jwtProvider;

    public GatewayPassportService(
            PassportCacheService passportCacheService,
            JwtProvider jwtProvider
    ) {
        this.passportCacheService = passportCacheService;
        this.jwtProvider = jwtProvider;
    }

    public PassportContext resolve(String accessToken) {
        Optional<PassportContext> cached = passportCacheService.get(accessToken);
        if (cached.isPresent()) {
            return cached.get();
        }

        DecodedAccessToken decoded = jwtProvider.decodeAccessToken(accessToken);
        PassportContext passportContext = new PassportContext(
                decoded.userId(),
                decoded.userType(),
                decoded.roles(),
                decoded.userVersion(),
                decoded.issuedAt(),
                decoded.expiresAt()
        );
        passportCacheService.put(accessToken, passportContext);
        return passportContext;
    }
}
