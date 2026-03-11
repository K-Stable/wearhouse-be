package com.wearhouse.common.security.current;

import java.util.List;

public record LoginUser(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
    public boolean isBuyer() {
        return "BUYER".equalsIgnoreCase(userType);
    }

    public boolean isSeller() {
        return "SELLER".equalsIgnoreCase(userType);
    }
}
