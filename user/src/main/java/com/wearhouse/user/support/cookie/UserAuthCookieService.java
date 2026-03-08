package com.wearhouse.user.support.cookie;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class UserAuthCookieService {

    private final String buyerAccessName;
    private final String sellerAccessName;
    private final String buyerRefreshName;
    private final String sellerRefreshName;
    private final boolean secure;
    private final String sameSite;
    private final String cookiePath;

    public UserAuthCookieService(
            @Value("${wearhouse.user.cookie.buyer-access-name:buyer_access_token}") String buyerAccessName,
            @Value("${wearhouse.user.cookie.seller-access-name:seller_access_token}") String sellerAccessName,
            @Value("${wearhouse.user.cookie.buyer-refresh-name:buyer_refresh_token}") String buyerRefreshName,
            @Value("${wearhouse.user.cookie.seller-refresh-name:seller_refresh_token}") String sellerRefreshName,
            @Value("${wearhouse.user.cookie.secure:false}") boolean secure,
            @Value("${wearhouse.user.cookie.same-site:Lax}") String sameSite,
            @Value("${wearhouse.user.cookie.path:/}") String cookiePath
    ) {
        this.buyerAccessName = buyerAccessName;
        this.sellerAccessName = sellerAccessName;
        this.buyerRefreshName = buyerRefreshName;
        this.sellerRefreshName = sellerRefreshName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.cookiePath = cookiePath;
    }

    public void writeBuyerTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        write(response, buyerAccessName, accessToken, 60L * 30);
        write(response, buyerRefreshName, refreshToken, 60L * 60 * 24 * 14);
    }

    public void writeSellerTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        write(response, sellerAccessName, accessToken, 60L * 30);
        write(response, sellerRefreshName, refreshToken, 60L * 60 * 24 * 14);
    }

    private void write(HttpServletResponse response, String name, String value, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(maxAgeSeconds)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
