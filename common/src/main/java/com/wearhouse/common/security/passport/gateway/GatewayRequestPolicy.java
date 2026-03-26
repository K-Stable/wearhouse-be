package com.wearhouse.common.security.passport.gateway;

public final class GatewayRequestPolicy {

    public static final String[] PUBLIC_URL_PATTERNS = {
            "/actuator/**",
            "/error",
            "/api/v1/auth/buyers/login",
            "/api/v1/auth/sellers/login",
            "/api/v1/auth/buyers/refresh",
            "/api/v1/auth/sellers/refresh",
            "/auth-service/api/v1/auth/buyers/login",
            "/auth-service/api/v1/auth/sellers/login",
            "/auth-service/api/v1/auth/buyers/refresh",
            "/auth-service/api/v1/auth/sellers/refresh",
            "/api/v1/users/buyers/signup",
            "/api/v1/users/sellers/signup",
            "/api/v1/users/buyers/email-code/send",
            "/api/v1/users/buyers/email-code/verify",
            "/api/v1/users/sellers/email-code/send",
            "/api/v1/users/sellers/email-code/verify",
            "/api/v1/users/buyers/login-id/availability",
            "/api/v1/users/sellers/login-id/availability",
            "/user-service/api/v1/users/buyers/signup",
            "/user-service/api/v1/users/sellers/signup",
            "/user-service/api/v1/users/buyers/email-code/send",
            "/user-service/api/v1/users/buyers/email-code/verify",
            "/user-service/api/v1/users/sellers/email-code/send",
            "/user-service/api/v1/users/sellers/email-code/verify",
            "/user-service/api/v1/users/buyers/login-id/availability",
            "/user-service/api/v1/users/sellers/login-id/availability"
    };

    public static final String BUYER_PATTERN = "/api/v1/buyer/**";
    public static final String BUYER_MYPAGE_PATTERN = "/api/v1/buyer/mypage/**";
    public static final String BUYER_CART_PATTERN = "/api/v1/buyer/carts/**";
    public static final String SELLER_PATTERN = "/api/v1/seller/**";

    private static final String BUYER_PATH_PREFIX = "/api/v1/buyer/";
    private static final String BUYER_MYPAGE_PREFIX = "/api/v1/buyer/mypage";
    private static final String BUYER_CART_PREFIX = "/api/v1/buyer/carts";

    private GatewayRequestPolicy() {
    }

    public static boolean isFrameworkPath(String path) {
        if (path == null) {
            return false;
        }
        return path.startsWith("/actuator") || path.startsWith("/error");
    }

    public static boolean isAuthOrSignupPublicPath(String path) {
        if (path == null) {
            return false;
        }
        String normalized = normalizeServicePrefixedPath(path);
        return "/api/v1/auth/buyers/login".equals(normalized)
                || "/api/v1/auth/sellers/login".equals(normalized)
                || "/api/v1/auth/buyers/refresh".equals(normalized)
                || "/api/v1/auth/sellers/refresh".equals(normalized)
                || "/api/v1/users/buyers/signup".equals(normalized)
                || "/api/v1/users/sellers/signup".equals(normalized)
                || "/api/v1/users/buyers/email-code/send".equals(normalized)
                || "/api/v1/users/buyers/email-code/verify".equals(normalized)
                || "/api/v1/users/sellers/email-code/send".equals(normalized)
                || "/api/v1/users/sellers/email-code/verify".equals(normalized)
                || "/api/v1/users/buyers/login-id/availability".equals(normalized)
                || "/api/v1/users/sellers/login-id/availability".equals(normalized);
    }

    public static boolean isPublicBuyerPath(String path) {
        if (path == null || !path.startsWith(BUYER_PATH_PREFIX)) {
            return false;
        }
        return !path.startsWith(BUYER_MYPAGE_PREFIX) && !path.startsWith(BUYER_CART_PREFIX);
    }

    private static String normalizeServicePrefixedPath(String path) {
        if (path.startsWith("/auth-service/")) {
            return path.substring("/auth-service".length());
        }
        if (path.startsWith("/user-service/")) {
            return path.substring("/user-service".length());
        }
        return path;
    }
}
