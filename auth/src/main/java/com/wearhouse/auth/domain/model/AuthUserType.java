package com.wearhouse.auth.domain.model;

public enum AuthUserType {
    BUYER,
    SELLER;

    public String role() {
        return "ROLE_" + name();
    }

    public String key() {
        return name().toLowerCase();
    }
}
