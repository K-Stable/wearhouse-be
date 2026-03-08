package com.wearhouse.inventory.support.security;

import java.util.List;

public record InventoryPassportPayload(
        Long userId,
        String userType,
        List<String> roles,
        Long userVersion
) {
}
