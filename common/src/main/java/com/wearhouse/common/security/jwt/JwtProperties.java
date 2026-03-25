package com.wearhouse.common.security.jwt;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "wearhouse.auth.jwt.secret")
public record JwtProperties(
        @Value("${wearhouse.auth.jwt.secret}") String secret,
        @Value("${wearhouse.auth.jwt.issuer:wearhouse-auth}") String issuer,
        @Value("${wearhouse.auth.jwt.access-token-minutes:30}") long accessTokenMinutes,
        @Value("${wearhouse.auth.refresh.ttl-days:14}") long refreshTokenDays,
        @Value("${wearhouse.auth.cookie.buyer-refresh-name:buyer_refresh_token}") String buyerRefreshCookieName,
        @Value("${wearhouse.auth.cookie.seller-refresh-name:seller_refresh_token}") String sellerRefreshCookieName,
        @Value("${wearhouse.auth.cookie.secure:false}") boolean cookieSecure,
        @Value("${wearhouse.auth.cookie.same-site:Lax}") String cookieSameSite,
        @Value("${wearhouse.auth.cookie.path:/}") String cookiePath,
        @Value("${wearhouse.auth.cookie.domain:}") String cookieDomain
) {
}
