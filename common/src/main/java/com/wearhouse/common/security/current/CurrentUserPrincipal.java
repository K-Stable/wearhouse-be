package com.wearhouse.common.security.current;

import java.util.List;

public record CurrentUserPrincipal(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
