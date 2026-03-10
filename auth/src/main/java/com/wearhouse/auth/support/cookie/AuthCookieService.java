package com.wearhouse.auth.support.cookie;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    private final String buyerAccessName;
    private final String sellerAccessName;
    private final String buyerRefreshName;
    private final String sellerRefreshName;
    private final boolean secure;
    private final String sameSite;
    private final String cookiePath;
    private final long refreshMaxAgeSeconds;

    public AuthCookieService(
            @Value("${wearhouse.auth.cookie.buyer-access-name:buyer_access_token}") String buyerAccessName,
            @Value("${wearhouse.auth.cookie.seller-access-name:seller_access_token}") String sellerAccessName,
            @Value("${wearhouse.auth.cookie.buyer-refresh-name:buyer_refresh_token}") String buyerRefreshName,
            @Value("${wearhouse.auth.cookie.seller-refresh-name:seller_refresh_token}") String sellerRefreshName,
            @Value("${wearhouse.auth.cookie.secure:false}") boolean secure,
            @Value("${wearhouse.auth.cookie.same-site:Lax}") String sameSite,
            @Value("${wearhouse.auth.cookie.path:/}") String cookiePath,
            @Value("${wearhouse.auth.refresh.ttl-days:14}") long refreshTtlDays
    ) {
        this.buyerAccessName = buyerAccessName;
        this.sellerAccessName = sellerAccessName;
        this.buyerRefreshName = buyerRefreshName;
        this.sellerRefreshName = sellerRefreshName;
        this.secure = secure;
        this.sameSite = sameSite;
        this.cookiePath = cookiePath;
        this.refreshMaxAgeSeconds = refreshTtlDays * 24 * 60 * 60;
    }

    public void writeBuyerTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        write(response, buyerAccessName, accessToken, 60L * 30);
        write(response, buyerRefreshName, refreshToken, 60L * 60 * 24 * 14);
    }

    public void writeSellerTokens(HttpServletResponse response, String accessToken, String refreshToken) {
        write(response, sellerAccessName, accessToken, 60L * 30);
        write(response, sellerRefreshName, refreshToken, 60L * 60 * 24 * 14);
    }

    public void writeBuyerRefreshToken(HttpServletResponse response, String refreshToken) {
        write(response, buyerRefreshName, refreshToken, refreshMaxAgeSeconds);
    }

    public void writeSellerRefreshToken(HttpServletResponse response, String refreshToken) {
        write(response, sellerRefreshName, refreshToken, refreshMaxAgeSeconds);
    }

    public void clearBuyerTokens(HttpServletResponse response) {
        clear(response, buyerAccessName);
        clear(response, buyerRefreshName);
    }

    public void clearSellerTokens(HttpServletResponse response) {
        clear(response, sellerAccessName);
        clear(response, sellerRefreshName);
    }

    public void clearBuyerRefreshToken(HttpServletResponse response) {
        clear(response, buyerRefreshName);
    }

    public void clearSellerRefreshToken(HttpServletResponse response) {
        clear(response, sellerRefreshName);
    }

    public String resolveBuyerRefreshToken(HttpServletRequest request) {
        return readCookie(request, buyerRefreshName);
    }

    public String resolveSellerRefreshToken(HttpServletRequest request) {
        return readCookie(request, sellerRefreshName);
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

    private void clear(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path(cookiePath)
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
