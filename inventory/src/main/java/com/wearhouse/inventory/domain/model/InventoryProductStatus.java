package com.wearhouse.inventory.domain.model;

public enum InventoryProductStatus {
    PENDING,
    RELEASED,
    SOLD_OUT,
    HIDDEN;

    public static String normalizeForFilter(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return null;
        }

        String token = normalizeToken(rawStatus);

        return switch (token) {
            case "ALL" -> null;
            case "PENDING" -> PENDING.name();
            case "RELEASE", "RELEASED" -> RELEASED.name();
            case "SOLDOUT" -> SOLD_OUT.name();
            case "HIDDEN" -> HIDDEN.name();
            default -> null;
        };
    }

    public static String normalizeForPersist(String rawStatus) {
        String normalized = normalizeForFilter(rawStatus);
        if (normalized == null) {
            return null;
        }
        return normalized;
    }

    public static boolean isAll(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return false;
        }
        return "ALL".equals(normalizeToken(rawStatus));
    }

    private static String normalizeToken(String rawStatus) {
        return rawStatus.trim()
                .toUpperCase()
                .replace("-", "")
                .replace("_", "")
                .replace(" ", "");
    }
}
