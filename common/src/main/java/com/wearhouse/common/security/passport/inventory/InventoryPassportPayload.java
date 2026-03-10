package com.wearhouse.common.security.passport.inventory;

import java.util.List;

public record InventoryPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
