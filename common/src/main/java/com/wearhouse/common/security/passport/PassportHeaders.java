package com.wearhouse.common.security.passport;

public final class PassportHeaders {

    public static final String USER = "X-Passport-User";
    public static final String SIGNATURE = "X-Passport-Sig";
    public static final String TIMESTAMP = "X-Passport-Ts";
    public static final String VERIFIED = "X-Passport-Verified";

    private PassportHeaders() {
    }
}
