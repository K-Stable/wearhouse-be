package com.wearhouse.common.security.passport.gateway;

public final class GatewayRequestPolicy {

    public static final String[] PUBLIC_URL_PATTERNS = {
            "/actuator/**",
            "/error",
            "/api/v1/auth/buyers/login",
            "/api/v1/auth/sellers/login",
            "/api/v1/users/buyers/signup",
            "/api/v1/users/sellers/signup"
    };

    public static final String BUYER_PATTERN = "/api/v1/buyer/**";
    public static final String BUYER_MYPAGE_PATTERN = "/api/v1/buyer/mypage/**";
    public static final String SELLER_PATTERN = "/api/v1/seller/**";

    private static final String BUYER_PATH_PREFIX = "/api/v1/buyer/";
    private static final String BUYER_MYPAGE_PREFIX = "/api/v1/buyer/mypage";

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
        return "/api/v1/auth/buyers/login".equals(path)
                || "/api/v1/auth/sellers/login".equals(path)
                || "/api/v1/users/buyers/signup".equals(path)
                || "/api/v1/users/sellers/signup".equals(path);
    }

    public static boolean isPublicBuyerPath(String path) {
        if (path == null || !path.startsWith(BUYER_PATH_PREFIX)) {
            return false;
        }
        return !path.startsWith(BUYER_MYPAGE_PREFIX);
    }
}
